//
//  AppState.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Observation

@Observable
@MainActor
final class AppState {
    var isAuthenticated: Bool
    var isOnboarded: Bool
    /// Browsing the community read-only without signing in. Session-only — never restored across a cold
    /// launch, so a relaunch always starts back at `LandingView`.
    var isGuest: Bool = false

    init(isAuthenticated: Bool = false, isOnboarded: Bool = false) {
        self.isAuthenticated = isAuthenticated
        self.isOnboarded = isOnboarded
    }
}
