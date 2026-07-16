//
//  ChamChamChamApp.swift
//  ChamChamCham
//
//  Created by iyungui on 7/2/26.
//

import SwiftUI
import SwiftData

@main
struct ChamChamChamApp: App {
    @State private var appState: AppState
    private let container: DIContainer

    init() {
        container = DIContainer(modelContainer: .makeApp())
        KakaoSDKBootstrap.initialize()
        NaverSDKBootstrap.initialize()
        _appState = State(initialValue: Self.initialAppState(container: container))
    }

    var body: some Scene {
        WindowGroup {
            RootView(container: container)
                .environment(appState)
                .onOpenURL { url in
                    if KakaoSDKBootstrap.handleOpenURL(url) { return }
                    _ = NaverSDKBootstrap.handleOpenURL(url)
                }
        }
        .modelContainer(container.modelContainer)
    }

    /// Derives cold-launch auth state synchronously, before SwiftUI's first render, so `RootView` never
    /// flashes the logged-out flow for a frame while `RootView.bootstrap()` awaits the actor-isolated
    /// `AuthTokenStore` (see its `hasStoredRefreshToken` doc comment).
    @MainActor
    private static func initialAppState(container: DIContainer) -> AppState {
        guard AuthTokenStore.hasStoredRefreshToken() else { return AppState() }
        guard let cached = CachedMemberProfile.fetchCached(in: container.modelContainer.mainContext) else {
            return AppState(isAuthenticated: true, isOnboarded: false)
        }
        return AppState(isAuthenticated: true, isOnboarded: cached.isOnboardingComplete)
    }
}
