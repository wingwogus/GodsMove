//
//  AppNotificationBadge.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `badge` (number). A pill/dot notification indicator: a `nil` count renders a 6pt dot,
/// 1–99 a fixed circle, and 100+ a wider pill capped at "999+".
struct AppNotificationBadge: View {
    enum Variant {
        case primary
        case new
    }

    /// `nil` renders the dot-only variant; otherwise the number is shown.
    let count: Int?
    var variant: Variant = .new

    var body: some View {
        switch count {
        case .none:
            Circle()
                .fill(backgroundColor)
                .frame(width: 6, height: 6)
        case let .some(value):
            Text(displayText(value))
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.inverse)
                .lineLimit(1)
                .padding(.horizontal, 8)
                .frame(minWidth: minWidth(value), minHeight: 28)
                .frame(height: 28)
                .background(backgroundColor)
                .clipShape(Capsule())
        }
    }

    private func displayText(_ value: Int) -> String {
        value > 999 ? "999+" : "\(value)"
    }

    /// 1–99 is a fixed 28pt circle; 100+ widens to at least 42pt.
    private func minWidth(_ value: Int) -> CGFloat {
        value < 100 ? 28 : 42
    }

    private var backgroundColor: Color {
        variant == .new ? Color.Object.red : Color.Object.primary
    }
}

#Preview {
    HStack(spacing: Spacing.md) {
        AppNotificationBadge(count: 1, variant: .new)
        AppNotificationBadge(count: 999, variant: .new)
        AppNotificationBadge(count: 1, variant: .primary)
        AppNotificationBadge(count: 1200, variant: .primary)
        AppNotificationBadge(count: nil, variant: .new)
    }
    .padding()
}
