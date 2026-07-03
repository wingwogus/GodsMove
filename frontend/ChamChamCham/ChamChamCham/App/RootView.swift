//
//  RootView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        if appState.isAuthenticated && appState.isOnboarded {
            MainTabView()
        } else {
            AuthFlowView()
        }
    }
}
