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
    var isAuthenticated = false
    var isOnboarded = false
}
