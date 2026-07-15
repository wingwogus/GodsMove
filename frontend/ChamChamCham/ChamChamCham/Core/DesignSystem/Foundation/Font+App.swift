//
//  Font+App.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI
import UIKit

/// Figma type scale (Pretendard). Size / letter-spacing / line-height come from the design
/// system spec; letter-spacing and line-height are expressed there as a percentage of size.
enum AppTypography {
    case headlineLarge
    case headlineLargeEmphasized
    case headlineMedium
    case headlineMediumEmphasized
    case titleLarge
    case titleLargeEmphasized
    case titleMedium
    case titleMediumEmphasized
    case bodyLarge
    case bodyLargeEmphasized
    case bodyMedium
    case bodyMediumEmphasized
    case labelMedium
    case labelMediumEmphasized

    var size: CGFloat {
        switch self {
        case .headlineLarge, .headlineLargeEmphasized: 32
        case .headlineMedium, .headlineMediumEmphasized: 28
        case .titleLarge, .titleLargeEmphasized: 24
        case .titleMedium, .titleMediumEmphasized: 20
        case .bodyLarge, .bodyLargeEmphasized: 18
        case .bodyMedium, .bodyMediumEmphasized: 16
        case .labelMedium, .labelMediumEmphasized: 15
        }
    }

    /// Line height as a percentage of `size`, matching the Figma spec.
    var lineHeightPercent: CGFloat {
        switch self {
        case .bodyLarge, .bodyLargeEmphasized, .bodyMedium, .bodyMediumEmphasized: 1.5
        default: 1.3
        }
    }

    /// Letter-spacing (tracking) as a percentage of `size`, matching the Figma spec.
    var trackingPercent: CGFloat {
        switch self {
        case .bodyLarge, .bodyLargeEmphasized, .bodyMedium, .bodyMediumEmphasized,
             .labelMedium, .labelMediumEmphasized:
            -0.02
        default:
            -0.01
        }
    }

    enum Weight {
        case medium
        case semibold
        case bold
    }

    /// Figma spec weights: non-emphasized tokens are Medium, and "-emphasized" tokens are SemiBold —
    /// except `headlineLargeEmphasized`, the largest page-title emphasis, which is Bold.
    var weight: Weight {
        switch self {
        case .headlineLargeEmphasized:
            .bold
        case .headlineLarge, .headlineMedium, .headlineMediumEmphasized, .titleLargeEmphasized,
             .titleMediumEmphasized, .bodyLargeEmphasized, .bodyMediumEmphasized, .labelMediumEmphasized:
            .semibold
        default:
            .medium
        }
    }

    private var fontName: String {
        switch weight {
        case .medium: "Pretendard-Medium"
        case .semibold: "Pretendard-SemiBold"
        case .bold: "Pretendard-Bold"
        }
    }

    private var fallbackWeight: UIFont.Weight {
        switch weight {
        case .medium: .medium
        case .semibold: .semibold
        case .bold: .bold
        }
    }

    var font: Font {
        .custom(fontName, size: size)
    }

    fileprivate var uiFont: UIFont {
        UIFont(name: fontName, size: size) ?? .systemFont(ofSize: size, weight: fallbackWeight)
    }
}

private struct AppTypographyModifier: ViewModifier {
    let style: AppTypography

    func body(content: Content) -> some View {
        let lineHeight = style.size * style.lineHeightPercent
        let lineSpacing = max(0, lineHeight - style.uiFont.lineHeight)
        content
            .font(style.font)
            .tracking(style.size * style.trackingPercent)
            .lineSpacing(lineSpacing)
    }
}

extension View {
    /// Applies a Figma type-scale token's font, letter-spacing, and line-height together.
    func appTypography(_ style: AppTypography) -> some View {
        modifier(AppTypographyModifier(style: style))
    }
}

// MARK: - Legacy shorthands

/// Best-effort mapping from the old system-font placeholders to the closest Figma token by
/// visual size/weight (not by name — Figma's "Headline" category is a page-title style, much
/// larger than SwiftUI's `.headline` text style). Revisit per-screen when polishing onboarding UI.
extension Font {
    static let appTitle = AppTypography.headlineMedium.font
    static let appHeadline = AppTypography.bodyLargeEmphasized.font
    static let appBody = AppTypography.bodyMedium.font
    static let appCaption = AppTypography.labelMedium.font
}
