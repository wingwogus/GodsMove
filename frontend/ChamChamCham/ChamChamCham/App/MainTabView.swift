//
//  MainTabView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct MainTabView: View {
    let container: DIContainer

    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("홈", systemImage: "house") }
            Text("영농기록")
                .tabItem { Label("영농기록", systemImage: "list.bullet") }
            CommunityView(container: container)
                .tabItem { Label("커뮤니티", systemImage: "person.3") }
            ProfileMainView(container: container)
                .tabItem { Label("마이페이지", systemImage: "person") }
        }
    }
}
