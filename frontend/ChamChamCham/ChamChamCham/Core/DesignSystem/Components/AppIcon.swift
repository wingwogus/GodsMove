//
//  AppIcon.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import SwiftUI

/// A leading/trailing icon glyph for design-system components. String literals resolve to SF
/// Symbols (`.system`) so existing call sites keep compiling unchanged; use `.asset` to render a
/// glyph from `Assets.xcassets/icon` as screens migrate off SF Symbol placeholders.
enum AppIconSource: Equatable, ExpressibleByStringLiteral {
    case system(String)
    case asset(String)

    init(stringLiteral value: String) {
        self = .system(value)
    }
}

/// Renders an `AppIconSource` at a fixed square size. SF Symbols use `.font(size:)` (their native
/// scaling mechanism); custom assets are made resizable and rendered per `renderingMode`.
struct AppIconView: View {
    let source: AppIconSource
    var size: CGFloat = 24
    /// `.template` tints the glyph with `.foregroundStyle`, matching SF Symbol behavior — the
    /// right default for monochrome UI glyphs. Multi-color assets (e.g. weather icons) need
    /// `.original` or their SVG fill colors get flattened into the current foreground color.
    var renderingMode: Image.TemplateRenderingMode = .template

    var body: some View {
        switch source {
        case let .system(name):
            Image(systemName: name)
                .font(.system(size: size))
                .frame(width: size, height: size)
        case let .asset(name):
            Image(name)
                .renderingMode(renderingMode)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        }
    }
}
