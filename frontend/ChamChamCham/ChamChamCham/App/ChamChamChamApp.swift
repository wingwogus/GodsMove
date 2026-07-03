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
    @State private var appState = AppState()
    private let modelContainer = ModelContainer.makeApp()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appState)
        }
        .modelContainer(modelContainer)
    }
}
