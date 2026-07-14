//
//  AppBadge.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `badge/label`. A small rounded tag rendered in one of four color treatments
/// (variant × style) at two sizes.
struct AppBadge: View {
    enum Size {
        case small
        case medium
    }

    /// `solid` fills with a bold color and white text; `solidPastel` uses a soft fill with
    /// tinted text. (Figma spells the token "soild"/"solid-pastel".)
    enum Style {
        case solid
        case solidPastel
    }

    enum Variant {
        case primary
        case secondary
    }

    let label: String
    var size: Size = .medium
    var style: Style = .solid
    var variant: Variant = .primary

    var body: some View {
        Text(label)
            .appTypography(.labelMedium)
            .foregroundStyle(textColor)
            .lineLimit(1)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, 8)
            .frame(minWidth: minWidth, minHeight: height)
            .background(backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var height: CGFloat { size == .medium ? 32 : 28 }
    private var minWidth: CGFloat { size == .medium ? 48 : 36 }
    private var horizontalPadding: CGFloat { size == .medium ? 10 : 8 }

    private var backgroundColor: Color {
        switch (variant, style) {
        case (.primary, .solid): return Color.Object.primary
        case (.secondary, .solid): return Color.Object.bold
        case (.primary, .solidPastel): return Color.Object.secondary
        case (.secondary, .solidPastel): return Color.Object.muted
        }
    }

    private var textColor: Color {
        switch (variant, style) {
        case (_, .solid): return Color.Text.inverse
        case (.primary, .solidPastel): return Color.Text.primary
        case (.secondary, .solidPastel): return Color.Text.subtle
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: Spacing.md) {
        HStack(spacing: Spacing.sm) {
            AppBadge(label: "레이블", style: .solid, variant: .primary)
            AppBadge(label: "레이블", style: .solidPastel, variant: .primary)
            AppBadge(label: "레이블", style: .solid, variant: .secondary)
            AppBadge(label: "레이블", style: .solidPastel, variant: .secondary)
        }
        HStack(spacing: Spacing.sm) {
            AppBadge(label: "레이블", size: .small, style: .solid, variant: .primary)
            AppBadge(label: "레이블", size: .small, style: .solidPastel, variant: .primary)
            AppBadge(label: "레이블", size: .small, style: .solid, variant: .secondary)
            AppBadge(label: "레이블", size: .small, style: .solidPastel, variant: .secondary)
        }
    }
    .padding()
}
