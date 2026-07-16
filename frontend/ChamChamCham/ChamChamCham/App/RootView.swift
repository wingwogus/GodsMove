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
        .task(id: appState.isAuthenticated && appState.isOnboarded) {
            guard appState.isAuthenticated,
                  appState.isOnboarded,
                  let memberId = CachedMemberProfile.fetchCached(in: modelContext)?.id else { return }
            await container.pendingFarmSyncService.syncPending(memberId: memberId)
        }
    }

    /// Cold-launch session reconciliation. `appState`'s initial value is already derived synchronously from
    /// the local cache in `ChamChamChamApp.initialAppState` (offline-first, no network wait, no logged-out
    /// flash) — this only handles the background server round-trip.
    private func bootstrap() async {
        guard appState.isAuthenticated else { return }

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
