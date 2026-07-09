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
    private let container: DIContainer

    init() {
        container = DIContainer(modelContainer: .makeApp())
        KakaoSDKBootstrap.initialize()
        NaverSDKBootstrap.initialize()
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
}
