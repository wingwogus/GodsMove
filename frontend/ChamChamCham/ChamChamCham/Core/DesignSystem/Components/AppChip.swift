//
//  AppChip.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `chip`. A selectable pill in one of two styles:
/// - `solidPastel`: selected = soft green fill + green border + green text.
/// - `solid`: selected = bold dark fill + white text; unselected = white fill + subtle border.
///
/// Pass `systemImage` for the optional trailing icon. (Figma spells the token "soild"/"soild-pastel".)
struct AppChip: View {
    enum Style {
        case solid
        case solidPastel
    }

    let label: String
    var isSelected: Bool = false
    var style: Style = .solidPastel
    var systemImage: String? = nil
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            HStack(spacing: 2) {
                Text(label)
                    .appTypography(.labelMedium)
                    .foregroundStyle(textColor)
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 16))
                        .foregroundStyle(textColor)
                        .frame(width: 24, height: 24)
                }
            }
            .padding(.vertical, 8)
            .padding(.leading, 12)
            .padding(.trailing, systemImage == nil ? 12 : 10)
            .frame(minWidth: 48, minHeight: 32)
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
        guard isSelected else {
            switch style {
            case .solid: return Color.Object.default
            case .solidPastel: return Color.Object.muted
            }
        }
        switch style {
        case .solid: return Color.Object.bold
        case .solidPastel: return Color.Object.primarySubtle
        }
    }

    private var textColor: Color {
        guard isSelected else { return Color.Text.subtle }
        switch style {
        case .solid: return Color.Text.inverse
        case .solidPastel: return Color.Text.primary
        }
    }

    private var borderColor: Color? {
        switch (style, isSelected) {
        case (.solidPastel, true):
            Color.Border.primary
        case (.solid, false):
            Color.Border.subtle
        default:
            nil
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
