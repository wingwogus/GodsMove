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

    init(isAuthenticated: Bool = false, isOnboarded: Bool = false) {
        self.isAuthenticated = isAuthenticated
        self.isOnboarded = isOnboarded
    }
}
