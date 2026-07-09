//
//  AppTabBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `tabs` (underline tab bar). The selected tab is bold with a 3pt green underline; others
/// are muted. Use `scrollable` for many fixed-width tabs (the Figma "10" segment); otherwise tabs
/// share the width equally.
struct AppTabBar: View {
    let titles: [String]
    @Binding var selection: Int
    /// Fixed-width, horizontally scrolling tabs instead of equal-width.
    var scrollable: Bool = false

    var body: some View {
        Group {
            if scrollable {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 0) { tabs }
                }
            } else {
                HStack(spacing: 0) { tabs }
            }
        }
        .background(Color.Background.default)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.Object.muted)
                .frame(height: 1)
        }
    }

    private var tabs: some View {
        ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
            let isSelected = index == selection
            Button {
                selection = index
            } label: {
                Text(title)
                    .appTypography(isSelected ? .titleMediumEmphasized : .titleMedium)
                    .foregroundStyle(isSelected ? Color.Text.default : Color.Text.muted)
                    .lineLimit(1)
                    .padding(12)
                    .frame(width: scrollable ? 104 : nil)
                    .frame(maxWidth: scrollable ? nil : .infinity)
                    .frame(height: 56)
                    .overlay(alignment: .bottom) {
                        if isSelected {
                            Rectangle()
                                .fill(Color.Border.primary)
                                .frame(height: 3)
                        }
                    }
            }
            .buttonStyle(.plain)
        }
    }
}

#Preview {
    struct Demo: View {
        @State private var a = 0
        @State private var b = 0
        var body: some View {
            VStack(spacing: Spacing.xl) {
                AppTabBar(titles: ["레이블", "레이블"], selection: $a)
                AppTabBar(titles: ["전체", "전초", "뿌리·껍질", "뿌리줄기", "잎", "꽃"],
                          selection: $b, scrollable: true)
            }
        }
    }
    return Demo()
}
