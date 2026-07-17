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
    private static let communityTabIndex = 2

    let container: DIContainer
    var isGuest: Bool = false

    @Environment(AppState.self) private var appState
    @State private var selection: Int
    @State private var loadedTabs: Set<Int>
    @State private var showLoginRequiredAlert = false

    init(container: DIContainer, isGuest: Bool = false) {
        self.container = container
        self.isGuest = isGuest
        let initialTab = isGuest ? Self.communityTabIndex : 0
        _selection = State(initialValue: initialTab)
        _loadedTabs = State(initialValue: [initialTab])
    }

    private var tabItems: [AppNavBar.Item] {
        [
            .init(title: "홈", icon: .asset("home_line"), selectedIcon: .asset("home")),
            .init(title: "영농 기록", icon: .asset("assignment-1"), selectedIcon: .asset("assignment")),
            .init(title: "정보 공유", icon: .asset("chat_bubble_line"), selectedIcon: .asset("chat_bubble")),
            .init(title: "프로필", icon: .asset("person_line"), selectedIcon: .asset("person")),
        ]
    }

    /// Passed to every tab instead of the raw `$selection` — a guest tapping any tab but 정보 공유 gets a
    /// login prompt instead of actually switching, so the other tabs (which all assume a signed-in member)
    /// never load.
    private var guardedSelection: Binding<Int> {
        Binding(
            get: { selection },
            set: { newValue in
                guard isGuest, newValue != Self.communityTabIndex else {
                    selection = newValue
                    return
                }
                showLoginRequiredAlert = true
            }
        )
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
        .loginRequiredAlert(isPresented: $showLoginRequiredAlert, appState: appState)
    }

    @ViewBuilder
    private func tabContent(_ index: Int) -> some View {
        switch index {
        case 0:
            HomeView(container: container, tabSelection: guardedSelection, tabItems: tabItems)
        case 1:
            RecordListView(
                container: container,
                repository: container.makeRecordRepository(),
                reportRepository: container.makeReportRepository(),
                weatherRepository: container.makeWeatherRepository(),
                mediaUpload: container.makeMediaUploadRepository(),
                voiceRepository: container.makeVoiceSessionRepository(),
                selection: guardedSelection,
                tabItems: tabItems
            )
        case 2:
            CommunityView(container: container, selection: guardedSelection, tabItems: tabItems, isGuest: isGuest)
        default:
            ProfileMainView(container: container, selection: guardedSelection, tabItems: tabItems)
        }
    }
}
