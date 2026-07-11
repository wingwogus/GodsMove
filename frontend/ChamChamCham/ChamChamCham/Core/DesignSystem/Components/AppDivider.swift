//
//  AppDivider.swift
//  ChamChamCham
//
//  Created by iyungui on 7/11/26.
//

import SwiftUI

/// Figma `divider`. A horizontal separator with flexible width and fixed height.
struct AppDivider: View {
    enum Size {
        case small
        case medium

        var height: CGFloat {
            switch self {
            case .small: 2
            case .medium: 4
            }
        }
    }

    static let color = Color.Object.muted

    var size: Size = .small

    var body: some View {
        Rectangle()
            .fill(Self.color)
            .frame(height: size.height)
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)
    }
}

#Preview {
    VStack(spacing: Spacing.lg) {
        AppDivider(size: .small)
        AppDivider(size: .medium)
    }
    .frame(width: 390)
    .padding()
}
