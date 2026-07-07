//
//  AppTopAppBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `top-app-bar`. Two layouts: `.standard` (large title on the left + up to two trailing
/// icons) and `.detail` (centered smaller title with optional leading/trailing icons). Icons are
/// SF Symbols until the Figma icon set lands.
struct AppTopAppBar: View {
    struct BarIcon {
        let systemName: String
        let action: () -> Void

        init(_ systemName: String, action: @escaping () -> Void = {}) {
            self.systemName = systemName
            self.action = action
        }
    }

    enum Background {
        case `default`
        case subtle
    }

    let title: String
    /// `.detail` centers the title and uses the smaller headline; `.standard` left-aligns it.
    var isDetail: Bool = false
    var background: Background = .default
    var showBorder: Bool = true
    var leading: BarIcon? = nil
    var trailing: [BarIcon] = []

    var body: some View {
        content
            .frame(height: isDetail ? 64 : nil)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.sm)
            .padding(.leading, isDetail ? 12 : Spacing.lg)
            .padding(.trailing, 12)
            .background(backgroundColor)
            .overlay(alignment: .bottom) {
                if showBorder {
                    Rectangle().fill(Color.Border.subtle).frame(height: 1)
                }
            }
    }

    @ViewBuilder private var content: some View {
        if isDetail {
            ZStack {
                Text(title)
                    .appTypography(.headlineMedium)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
                HStack {
                    iconSlot(leading)
                    Spacer()
                    iconSlot(trailing.first)
                }
            }
        } else {
            HStack(spacing: 0) {
                Text(title)
                    .appTypography(.headlineLarge)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
                Spacer()
                ForEach(Array(trailing.enumerated()), id: \.offset) { _, icon in
                    iconButton(icon)
                }
            }
        }
    }

    @ViewBuilder private func iconSlot(_ icon: BarIcon?) -> some View {
        if let icon {
            iconButton(icon)
        } else {
            Color.clear.frame(width: 48, height: 48)
        }
    }

    private func iconButton(_ icon: BarIcon) -> some View {
        Button(action: icon.action) {
            Image(systemName: icon.systemName)
                .font(.system(size: 26))
                .frame(width: 48, height: 48)
                .foregroundStyle(Color.Icon.default)
        }
        .buttonStyle(.plain)
    }

    private var backgroundColor: Color {
        background == .subtle ? Color.Background.subtle : Color.Background.default
    }
}

#Preview {
    VStack(spacing: Spacing.xl) {
        AppTopAppBar(
            title: "타이틀",
            trailing: [.init("bell"), .init("gearshape")]
        )
        AppTopAppBar(
            title: "타이틀",
            isDetail: true,
            leading: .init("chevron.left"),
            trailing: [.init("ellipsis")]
        )
    }
}
