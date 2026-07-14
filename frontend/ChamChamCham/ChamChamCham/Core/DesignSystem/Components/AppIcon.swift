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
/// scaling mechanism); custom assets are made resizable and template-tinted so `.foregroundStyle`
/// behaves the same as it does for SF Symbols.
struct AppIconView: View {
    let source: AppIconSource
    var size: CGFloat = 24

    var body: some View {
        switch source {
        case let .system(name):
            Image(systemName: name)
                .font(.system(size: size))
                .frame(width: size, height: size)
        case let .asset(name):
            Image(name)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        }
    }
}
