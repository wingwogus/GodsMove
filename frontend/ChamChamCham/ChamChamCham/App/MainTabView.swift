//
//  MainTabView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

/// App shell: swaps the native `TabView` for the design-system `AppNavBar` (Figma `nav-bar`).
///
/// Each tab owns its own full-screen `NavigationStack` and docks `AppNavBar` *inside* that stack's
/// root (via `appTabBarDock`), as a `VStack` sibling below the content. Two things fall out of that:
/// pushing a detail view slides it over the bar and a pop reveals it again — the native
/// `hidesBottomBarWhenPushed` behavior, which a `TabView`-less custom bar can't get from
/// `.toolbar(.hidden, for: .tabBar)`. And because the bar is a sibling (not a `safeAreaInset`, whose
/// value greedy `ScrollView`/`ZStack` content ignores), the content region is *physically* bounded
/// above it, so bottom-anchored FABs (community/record 작성 버튼) don't draw under it.
///
/// Tabs are kept alive across switches (hidden with `.opacity`) instead of rebuilt — matching native
/// `TabView` — and built lazily on first selection (`loadedTabs`) so a cold launch only runs the
/// initial tab's `.task`.
struct MainTabView: View {
    let container: DIContainer

    @State private var selection = 0
    @State private var loadedTabs: Set<Int> = [0]

    private var tabItems: [AppNavBar.Item] {
        [
            .init(title: "홈", icon: .asset("home_line"), selectedIcon: .asset("home")),
            .init(title: "영농 기록", icon: .asset("assignment-1"), selectedIcon: .asset("assignment")),
            .init(title: "정보 공유", icon: .asset("chat_bubble_line"), selectedIcon: .asset("chat_bubble")),
            .init(title: "프로필", icon: .asset("person_line"), selectedIcon: .asset("person")),
        ]
    }

    var body: some View {
        ZStack {
            ForEach(Array(tabItems.indices), id: \.self) { index in
                if loadedTabs.contains(index) {
                    tabContent(index)
                        .opacity(selection == index ? 1 : 0)
                        .allowsHitTesting(selection == index)
                        .accessibilityHidden(selection != index)
                }
            }
        }
        .onChange(of: selection) { _, newValue in
            loadedTabs.insert(newValue)
        }
    }

    @ViewBuilder
    private func tabContent(_ index: Int) -> some View {
        switch index {
        case 0:
            HomeView(container: container, tabSelection: $selection, tabItems: tabItems)
        case 1:
            RecordListView(
                repository: container.makeRecordRepository(),
                reportRepository: container.makeReportRepository(),
                weatherRepository: container.makeWeatherRepository(),
                mediaUpload: container.makeMediaUploadRepository(),
                selection: $selection,
                tabItems: tabItems
            )
        case 2:
            CommunityView(container: container, selection: $selection, tabItems: tabItems)
        default:
            ProfileMainView(container: container, selection: $selection, tabItems: tabItems)
        }
    }
}
