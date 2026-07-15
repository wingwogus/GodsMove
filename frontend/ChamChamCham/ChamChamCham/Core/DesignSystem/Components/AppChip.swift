//
//  AppChip.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `chip`. A selectable pill in one of two styles:
/// - `solidPastel`: selected = soft green fill + green border + green text.
/// - `solid`: selected = bold dark fill + white text; unselected = muted gray fill.
///
/// `systemImage` remains the legacy trailing icon. Use `leadingSystemImage` and
/// `trailingSystemImage` to match Figma's separate icon properties.
struct AppChip: View {
    enum Style {
        case solid
        case solidPastel
    }

    let label: String
    var isSelected: Bool = false
    var style: Style = .solidPastel
    var systemImage: AppIconSource? = nil
    var leadingSystemImage: AppIconSource? = nil
    var trailingSystemImage: AppIconSource? = nil
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            HStack(spacing: 2) {
                if let leadingSystemImage {
                    icon(leadingSystemImage)
                }
                Text(label)
                    .appTypography(isSelected ? .labelMediumEmphasized : .labelMedium)
                    .foregroundStyle(textColor)
                if let trailingIcon {
                    icon(trailingIcon)
                }
            }
            .padding(.vertical, 4)
            .padding(.leading, Self.leadingPadding(hasLeadingIcon: leadingSystemImage != nil))
            .padding(.trailing, Self.trailingPadding(hasTrailingIcon: trailingIcon != nil))
            .frame(minWidth: 48, minHeight: 32, maxHeight: 32)
            .background(backgroundColor)
            .overlay {
                if let borderColor {
                    Capsule().stroke(borderColor, lineWidth: 1)
                }
            }
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var backgroundColor: Color {
        Self.fillColor(style: style, isSelected: isSelected)
    }

    private var textColor: Color {
        guard isSelected else { return Color.Text.subtle }
        switch style {
        case .solid: return Color.Text.inverse
        case .solidPastel: return Color.Text.primary
        }
    }

    private var borderColor: Color? {
        Self.borderColor(style: style, isSelected: isSelected)
    }

    private var trailingIcon: AppIconSource? {
        trailingSystemImage ?? systemImage
    }

    private func icon(_ source: AppIconSource) -> some View {
        AppIconView(source: source, size: 24)
            .foregroundStyle(textColor)
    }

    static func leadingPadding(hasLeadingIcon: Bool) -> CGFloat {
        hasLeadingIcon ? 8 : 12
    }

    static func trailingPadding(hasTrailingIcon: Bool) -> CGFloat {
        hasTrailingIcon ? 8 : 12
    }

    static func fillColor(style: Style, isSelected: Bool) -> Color {
        switch (style, isSelected) {
        case (.solid, true): Color.Object.bold
        case (.solid, false): Color.Object.muted
        case (.solidPastel, true): Color.Object.primarySubtle
        case (.solidPastel, false): Color.Object.default
        }
    }

    static func borderColor(style: Style, isSelected: Bool) -> Color? {
        switch (style, isSelected) {
        case (.solidPastel, true): Color.Border.primary
        case (.solidPastel, false): Color.Border.subtle
        case (.solid, _): nil
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: Spacing.md) {
        HStack(spacing: Spacing.sm) {
            AppChip(label: "레이블", isSelected: true, style: .solid)
            AppChip(label: "레이블", isSelected: true, style: .solid, systemImage: "checkmark")
        }
        HStack(spacing: Spacing.sm) {
            AppChip(label: "레이블", isSelected: true, style: .solidPastel)
            AppChip(label: "레이블", isSelected: true, style: .solidPastel, systemImage: "checkmark")
        }
        HStack(spacing: Spacing.sm) {
            AppChip(label: "레이블", isSelected: false, style: .solid)
            AppChip(label: "레이블", isSelected: false, style: .solid, systemImage: "checkmark")
        }
    }
    .padding()
}
