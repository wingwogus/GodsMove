//
//  Color+App.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// Figma "Primitives" collection. Not for direct use in views — reference the semantic
/// tokens below (`Color.Background`, `Color.Object`, `Color.Text`, `Color.Icon`, `Color.Border`).
private extension Color {
    enum Green {
        static let c50 = Color(hex: 0xF5FCF3)
        static let c100 = Color(hex: 0xE4F8E3)
        static let c200 = Color(hex: 0xC6F1CB)
        static let c300 = Color(hex: 0xA5E9B1)
        static let c400 = Color(hex: 0x7FE19E)
        static let c500 = Color(hex: 0x59D092)
        static let c600 = Color(hex: 0x38C284)
        static let c700 = Color(hex: 0x2DA972)
        static let c800 = Color(hex: 0x27865C)
        static let c900 = Color(hex: 0x1B5D3F)
    }

    enum Lime {
        static let c50 = Color(hex: 0xF9FCF3)
        static let c100 = Color(hex: 0xF2FAE1)
        static let c200 = Color(hex: 0xE6F7BF)
        static let c300 = Color(hex: 0xD8F698)
        static let c400 = Color(hex: 0xC8F468)
        static let c500 = Color(hex: 0xBAED4F)
        static let c600 = Color(hex: 0xA3E31A)
        static let c700 = Color(hex: 0x8CC610)
        static let c800 = Color(hex: 0x699018)
        static let c900 = Color(hex: 0x587911)
    }

    enum Gray {
        static let c0 = Color(hex: 0xFFFFFF)
        static let c50 = Color(hex: 0xFAFAFA)
        static let c100 = Color(hex: 0xF3F3F3)
        static let c200 = Color(hex: 0xE0E0E0)
        static let c300 = Color(hex: 0xCFCFCF)
        static let c400 = Color(hex: 0xACACAC)
        static let c500 = Color(hex: 0x878787)
        static let c600 = Color(hex: 0x686868)
        static let c700 = Color(hex: 0x4F4F4F)
        static let c800 = Color(hex: 0x343434)
        static let c900 = Color(hex: 0x1A1A1A)
    }

    enum Red {
        static let c50 = Color(hex: 0xFEF2F2)
        static let c100 = Color(hex: 0xFEE2E2)
        static let c200 = Color(hex: 0xFECACA)
        static let c300 = Color(hex: 0xFCA5A5)
        static let c400 = Color(hex: 0xF87171)
        static let c500 = Color(hex: 0xEF4444)
        static let c600 = Color(hex: 0xDC2626)
        static let c700 = Color(hex: 0xB91C1C)
        static let c800 = Color(hex: 0x991B1B)
        static let c900 = Color(hex: 0x7F1D1D)
    }
}

// MARK: - Semantic tokens (Figma "Light" mode)

extension Color {
    enum Background {
        static let `default` = Gray.c0
        static let subtle = Gray.c50
    }

    enum Object {
        static let `default` = Gray.c0
        static let subtle = Gray.c50
        static let muted = Gray.c100
        static let strong = Gray.c200
        static let bold = Gray.c800
        static let primary = Green.c600
        static let primarySubtle = Green.c100
        static let secondary = Lime.c200
        static let secondarySubtle = Lime.c50
        static let disabled = Gray.c200
        static let disabledSubtle = Gray.c100
        static let red = Red.c500
        static let redSubtle = Red.c100
    }

    enum Text {
        static let `default` = Gray.c900
        static let subtle = Gray.c700
        static let muted = Gray.c500
        static let disabled = Gray.c400
        static let inverse = Gray.c0
        static let primary = Green.c800
        static let secondary = Lime.c800
        static let red = Red.c500
    }

    enum Icon {
        static let `default` = Gray.c800
        static let subtle = Gray.c600
        static let disabled = Gray.c400
        static let inverse = Gray.c0
        static let primary = Green.c700
        static let red = Red.c500
    }

    enum Border {
        static let `default` = Gray.c200
        static let subtle = Gray.c100
        static let strong = Gray.c400
        static let primary = Green.c600
        static let disabled = Gray.c300
        static let error = Red.c500
    }

    enum Chart {
        static let primary = Color(hex: 0x38C284)
        static let green300 = Color(hex: 0xA5E9B1)
        static let yellow = Color(hex: 0xF7DC11)
        static let lime = Color(hex: 0xC8F468)
        static let turquoise = Color(hex: 0x81DAD8)
        static let blue = Color(hex: 0xB1CBDF)

        static let palette = [primary, green300, yellow, lime, turquoise, blue]
    }
}

extension Color {
    /// Scrim/dim behind full-screen overlays such as the record speed-dial (`Gray/900` @ 64%).
    /// Baked-in opacity so the same value can back both the content region and the nav bar.
    static let scrim = Gray.c900.opacity(0.64)
}

// MARK: - Legacy shorthands

extension Color {
    static let appPrimary = Object.primary
    static let appBackground = Background.default
    static let appTextPrimary = Text.default
    static let appTextSecondary = Text.subtle
}
