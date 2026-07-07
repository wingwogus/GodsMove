//
//  RootView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftData
import SwiftUI

struct RootView: View {
    let container: DIContainer
    @Environment(AppState.self) private var appState
    @Environment(\.modelContext) private var modelContext

    var body: some View {
        Group {
            if appState.isAuthenticated && appState.isOnboarded {
                MainTabView(container: container)
            } else {
                AuthFlowView(container: container)
            }
        }
        .task { await bootstrap() }
        .task { await observeSessionExpiry() }
    }

    /// Cold-launch auto-login: routes off the local cache immediately (offline-first, no network wait),
    /// then reconciles with the server in the background.
    private func bootstrap() async {
        guard await container.authTokenStore.refreshToken() != nil else { return }

        if let cached = CachedMemberProfile.fetchCached(in: modelContext) {
            appState.isAuthenticated = true
            appState.isOnboarded = cached.isOnboardingComplete
        } else {
            // Refresh token survived (e.g. Keychain persists across an app delete+reinstall) but there's no
            // local cache row — conservatively resume onboarding rather than optimistically unlocking MainTabView.
            appState.isAuthenticated = true
            appState.isOnboarded = false
        }

        // Proactively rotate the token and detect server-side revocation early — the backend keeps only a
        // single active refresh token per member, so a login on another device silently invalidates this
        // one. Without this, a stale "logged in" UI would only be caught several screens later on the first
        // 401. A network failure here must NOT sign the user out — only an explicit unauthorized rejection
        // does, and that's handled inside the coordinator itself (see `observeSessionExpiry`).
        try? await container.tokenRefreshCoordinator.refreshIfNeeded()
    }

    private func observeSessionExpiry() async {
        let sessionExpiredEvents = await container.authTokenStore.sessionExpiredEvents()
        for await _ in sessionExpiredEvents {
            appState.isAuthenticated = false
            appState.isOnboarded = false
        }
    }
}
