//
//  AppButton.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `button`. One view covering the full component set: 4 variants × 4 sizes × 3 types
/// (label / icon / label+icon). The type is derived from which of `title` / `systemImage` are
/// provided. Disabled state comes from the `.disabled(_:)` environment.
///
/// Icons are SF Symbols for now — the Figma custom icon set isn't in the codebase yet; swap to it
/// once that component lands. `xlarge` is an icon-only size in the design; label variants fall back
/// to `large` metrics.
struct AppButton: View {
    enum Variant {
        case primary
        case secondary
        case tertiary
        case neutral
    }

    enum Size {
        case small
        case medium
        case large
        case xlarge
    }

    let title: String?
    var systemImage: String?
    var variant: Variant = .primary
    var size: Size = .medium
    /// Stretch label buttons to fill available width (icon-only buttons ignore this).
    var fullWidth: Bool = false
    /// Renders the disabled appearance (grey fill / muted text) while keeping the button tappable.
    /// Use for submit buttons that should stay pressable to surface validation errors even when the
    /// form isn't yet valid. `.disabled(_:)` still fully disables both look and interaction.
    var appearsDisabled: Bool = false
    let action: () -> Void

    @Environment(\.isEnabled) private var isEnabled

    init(
        _ title: String? = nil,
        systemImage: String? = nil,
        variant: Variant = .primary,
        size: Size = .medium,
        fullWidth: Bool = false,
        appearsDisabled: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.variant = variant
        self.size = size
        self.fullWidth = fullWidth
        self.appearsDisabled = appearsDisabled
        self.action = action
    }

    /// True when either the environment disabled the button or the caller asked for a disabled look.
    private var rendersDisabled: Bool { !isEnabled || appearsDisabled }

    var body: some View {
        Button(action: action) {
            content
                .foregroundStyle(foreground)
                .background(background)
                .clipShape(shape)
                .overlay {
                    if let borderColor {
                        shape.stroke(borderColor, lineWidth: 1)
                    }
                }
        }
        .buttonStyle(PressableButtonStyle())
    }

    // MARK: - Content

    private enum Kind { case label, icon, labelIcon }

    private var kind: Kind {
        switch (title, systemImage) {
        case (.some, .some): return .labelIcon
        case (.none, .some): return .icon
        default: return .label
        }
    }

    @ViewBuilder private var content: some View {
        switch kind {
        case .icon:
            Image(systemName: systemImage ?? "")
                .font(.system(size: iconGlyphSize))
                .frame(width: iconButtonSize, height: iconButtonSize)
        case .label:
            Text(title ?? "")
                .appTypography(font)
                .padding(.horizontal, labelHorizontalPadding)
                .frame(height: height)
                .frame(maxWidth: fullWidth ? .infinity : nil)
        case .labelIcon:
            HStack(spacing: gap) {
                Image(systemName: systemImage ?? "")
                    .font(.system(size: iconGlyphSize))
                Text(title ?? "")
                    .appTypography(font)
            }
            .padding(.leading, leadingPadding)
            .padding(.trailing, trailingPadding)
            .frame(height: height)
            .frame(maxWidth: fullWidth ? .infinity : nil)
        }
    }

    private var shape: AnyShape {
        kind == .icon ? AnyShape(Capsule()) : AnyShape(RoundedRectangle(cornerRadius: cornerRadius))
    }

    // MARK: - Colors

    private var foreground: Color {
        if rendersDisabled { return Color.Text.muted }
        switch variant {
        case .primary, .secondary: return Color.Text.inverse
        case .tertiary: return Color.Text.primary
        case .neutral: return Color.Text.default
        }
    }

    private var background: Color {
        if rendersDisabled {
            return variant == .neutral ? Color.Object.subtle : Color.Object.disabled
        }
        switch variant {
        case .primary: return Color.Object.primary
        case .secondary: return Color.Object.bold
        case .tertiary: return Color.Object.secondary
        case .neutral: return Color.Object.subtle
        }
    }

    private var borderColor: Color? {
        variant == .neutral ? Color.Border.default : nil
    }

    // MARK: - Metrics

    private var cornerRadius: CGFloat {
        size == .large || size == .xlarge ? 16 : 12
    }

    private var height: CGFloat {
        switch size {
        case .small: return 48
        case .medium: return 56
        case .large, .xlarge: return 64
        }
    }

    private var font: AppTypography {
        switch size {
        case .small: return .bodyMedium
        case .medium: return .bodyLarge
        case .large, .xlarge: return .titleMedium
        }
    }

    private var iconButtonSize: CGFloat {
        switch size {
        case .small: return 48
        case .medium: return 56
        case .large: return 64
        case .xlarge: return 72
        }
    }

    private var iconGlyphSize: CGFloat {
        switch kind {
        case .icon:
            switch size {
            case .small: return 24
            case .medium, .large: return 32
            case .xlarge: return 40
            }
        default: // label+icon
            return size == .large || size == .xlarge ? 32 : 24
        }
    }

    private var gap: CGFloat { size == .large || size == .xlarge ? 6 : 4 }

    private var labelHorizontalPadding: CGFloat { size == .large || size == .xlarge ? 20 : 16 }

    private var leadingPadding: CGFloat {
        switch size {
        case .small: return 14
        case .medium: return 16
        case .large, .xlarge: return 20
        }
    }

    private var trailingPadding: CGFloat {
        switch size {
        case .small: return 16
        case .medium: return 20
        case .large, .xlarge: return 24
        }
    }
}

/// Subtle press feedback shared by design-system buttons.
private struct PressableButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

#Preview {
    ScrollView {
        VStack(alignment: .leading, spacing: Spacing.md) {
            AppButton("레이블", variant: .primary, size: .large) {}
            AppButton("레이블", systemImage: "checkmark", variant: .secondary, size: .medium) {}
            AppButton("레이블", variant: .tertiary, size: .medium) {}
            AppButton("레이블", variant: .neutral, size: .small) {}
            AppButton("레이블", variant: .primary, size: .medium) {}.disabled(true)
            AppButton("전폭 버튼", variant: .primary, size: .medium, fullWidth: true) {}
            HStack(spacing: Spacing.sm) {
                AppButton(systemImage: "plus", variant: .primary, size: .small) {}
                AppButton(systemImage: "plus", variant: .secondary, size: .medium) {}
                AppButton(systemImage: "plus", variant: .tertiary, size: .large) {}
                AppButton(systemImage: "plus", variant: .primary, size: .xlarge) {}
            }
        }
        .padding()
    }
}
