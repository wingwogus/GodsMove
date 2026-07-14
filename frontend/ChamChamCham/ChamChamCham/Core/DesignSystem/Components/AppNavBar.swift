//
//  AppNavBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `nav-bar`. Bottom tab bar with equal-width items: the selected item shows a filled icon
/// and a bold label; others use line icons and a muted medium label.
///
/// Note: the Figma selected label is `label/medium-emphasized` = **Bold 15**, but the codebase's
/// `AppTypography.labelMediumEmphasized` is defined as Medium. Until that token is reconciled, the
/// selected label is rendered with Pretendard-Bold explicitly to match the design.
struct AppNavBar: View {
    struct Item: Identifiable {
        let id = UUID()
        let title: String
        let icon: AppIconSource
        let selectedIcon: AppIconSource

        init(title: String, icon: AppIconSource, selectedIcon: AppIconSource) {
            self.title = title
            self.icon = icon
            self.selectedIcon = selectedIcon
        }
    }

    let items: [Item]
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: Spacing.md) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                let isSelected = index == selection
                Button {
                    selection = index
                } label: {
                    VStack(spacing: Spacing.xs) {
                        AppIconView(source: isSelected ? item.selectedIcon : item.icon, size: 24)
                            .foregroundStyle(isSelected ? Color.Icon.default : Color.Icon.subtle)
                        Text(item.title)
                            .appTypography(.labelMedium)
                            .font(.custom(isSelected ? "Pretendard-Bold" : "Pretendard-Medium", size: 15))
                            .foregroundStyle(isSelected ? Color.Text.default : Color.Text.subtle)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 12)
                    .padding(.vertical, Spacing.sm)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(height: 72, alignment: .top)
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.sm)
        .background(Color.Background.default)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color.Border.subtle)
                .frame(height: 1)
        }
    }
}

#Preview {
    struct Demo: View {
        @State private var selection = 0
        var body: some View {
            VStack {
                Spacer()
                AppNavBar(
                    items: [
                        .init(title: "홈", icon: .asset("home_line"), selectedIcon: .asset("home")),
                        .init(title: "영농 기록", icon: .asset("assignment-1"), selectedIcon: .asset("assignment")),
                        .init(title: "정보 공유", icon: .asset("forum_line"), selectedIcon: .asset("forum")),
                        .init(title: "프로필", icon: .asset("person_line"), selectedIcon: .asset("person")),
                    ],
                    selection: $selection
                )
            }
        }
    }
    return Demo()
}
