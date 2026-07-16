//
//  MainTabView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

/// App shell: swaps the native `TabView` for the design-system `AppNavBar` (Figma `nav-bar`).
///
/// The nav bar is a `VStack` sibling (not a `safeAreaInset`) so the content region is *physically*
/// bounded above the bar — matching how the native tab bar shrank its children. A plain
/// `safeAreaInset` only shrinks the safe-area *value*, which greedy `ScrollView`/`ZStack` content
/// ignores, leaving bottom-anchored FABs (community/record 작성 버튼) drawn under the bar. Only the
/// bar's background bleeds into the home-indicator area.
///
/// Each tab owns its own `NavigationStack`, so tab content is kept alive across switches instead of
/// being rebuilt — matching native `TabView` behavior. Tabs are built lazily on first selection
/// (via `loadedTabs`) so a cold launch only runs the initial tab's `.task`, then stay in the
/// hierarchy (hidden with `.opacity`) to preserve navigation and scroll state.
struct MainTabView: View {
    let container: DIContainer

    @State private var selection = 0
    @State private var loadedTabs: Set<Int> = [0]
    /// The record tab's speed-dial open state, hoisted here so the same value dims the content region
    /// (inside `RecordListView`) and the nav bar (below) in one animation transaction — otherwise the
    /// `VStack`-bounded content scrim can't reach over the sibling nav bar and the bar stays lit.
    @State private var isSpeedDialOpen = false

    private var tabItems: [AppNavBar.Item] {
        [
            .init(title: "홈", icon: .asset("home_line"), selectedIcon: .asset("home")),
            .init(title: "영농 기록", icon: .asset("assignment-1"), selectedIcon: .asset("assignment")),
            .init(title: "정보 공유", icon: .asset("chat_bubble_line"), selectedIcon: .asset("chat_bubble")),
            .init(title: "프로필", icon: .asset("person_line"), selectedIcon: .asset("person")),
        ]
    }

    var body: some View {
        VStack(spacing: 0) {
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
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            AppNavBar(items: tabItems, selection: $selection)
                .background(Color.Background.default.ignoresSafeArea(edges: .bottom))
                .overlay {
                    // Second half of the speed-dial scrim: covers the nav bar (and its home-indicator
                    // background) so the whole screen darkens as one. Blocks tab switches while open;
                    // tapping dismisses, matching the content scrim.
                    if isSpeedDialOpen {
                        Color.scrim
                            .ignoresSafeArea(edges: .bottom)
                            .transition(.opacity)
                            .onTapGesture {
                                withAnimation(.easeInOut(duration: 0.15)) { isSpeedDialOpen = false }
                            }
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
            HomeView(container: container, tabSelection: $selection)
        case 1:
            RecordListView(
                repository: container.makeRecordRepository(),
                mediaUpload: container.makeMediaUploadRepository(),
                isSpeedDialOpen: $isSpeedDialOpen
            )
        case 2:
            CommunityView(container: container)
        default:
            ProfileMainView(container: container)
        }
    }
}
