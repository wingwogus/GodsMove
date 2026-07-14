//
//  AppAvatar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `avatar`. Supports image-backed circular avatars and the neutral person placeholder.
struct AppAvatar<Thumbnail: View>: View {
    enum Size {
        case large
        case medium
        case small
        case xSmall

        var dimension: CGFloat {
            switch self {
            case .large: 96
            case .medium: 48
            case .small: 32
            case .xSmall: 24
            }
        }
    }

    var size: Size = .medium
    private let thumbnail: Thumbnail?

    init(size: Size = .medium, @ViewBuilder thumbnail: () -> Thumbnail) {
        self.size = size
        self.thumbnail = thumbnail()
    }

    var body: some View {
        ZStack {
            if let thumbnail {
                thumbnail
            } else {
                placeholder
            }
        }
        .frame(width: dimension, height: dimension)
        .clipShape(Circle())
    }

    private var dimension: CGFloat { size.dimension }

    private var placeholder: some View {
        ZStack {
            Circle()
                .fill(Color.Object.subtle)
                .overlay(Circle().stroke(Color.Border.default, lineWidth: 1))

            VStack(spacing: dimension * 0.08) {
                Circle()
                    .fill(Color.Text.disabled)
                    .frame(width: dimension * 0.34, height: dimension * 0.34)

                Ellipse()
                    .fill(Color.Text.disabled)
                    .frame(width: dimension * 0.62, height: dimension * 0.28)
            }
            .offset(y: dimension * 0.08)
        }
    }
}

extension AppAvatar where Thumbnail == EmptyView {
    init(size: Size = .medium) {
        self.size = size
        self.thumbnail = nil
    }
}

#Preview {
    HStack(alignment: .center, spacing: Spacing.lg) {
        AppAvatar(size: .large) {
            AppImagePlaceholder(isCircle: true, squareSize: 12)
        }
        AppAvatar(size: .large)
        AppAvatar(size: .medium) {
            AppImagePlaceholder(isCircle: true, squareSize: 8)
        }
        AppAvatar(size: .medium)
        AppAvatar(size: .small) {
            AppImagePlaceholder(isCircle: true, squareSize: 6)
        }
        AppAvatar(size: .small)
    }
    .padding()
}
