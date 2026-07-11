//
//  AppCard.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import SwiftUI

/// Figma `card`. The four sizes are distinct compositions, rather than one layout scaled up or down.
struct AppCard<Thumbnail: View>: View {
    enum Size {
        case xsmall
        case small
        case medium
        case large

        var canvasSize: CGSize {
            switch self {
            case .xsmall: CGSize(width: 168, height: 168)
            case .small: CGSize(width: 350, height: 180)
            case .medium: CGSize(width: 258, height: 261)
            case .large: CGSize(width: 350, height: 334)
            }
        }

        var cornerRadius: CGFloat {
            switch self {
            case .xsmall, .small: 16
            case .medium: 20
            case .large: 24
            }
        }

        var padding: CGFloat {
            switch self {
            case .xsmall: 12
            case .medium: 16
            case .small, .large: 20
            }
        }
    }

    let size: Size
    let title: String
    var captions: [String] = []
    var badges: [String] = []
    var dateText: String = "mm/dd"
    var nickname: String = "닉네임"
    var likeText: String = "nn"
    var commentText: String = "nn"
    var showsPostInfo: Bool = true
    var width: CGFloat? = nil

    private let thumbnail: Thumbnail?

    init(
        size: Size,
        title: String,
        captions: [String] = [],
        badges: [String] = [],
        dateText: String = "mm/dd",
        nickname: String = "닉네임",
        likeText: String = "nn",
        commentText: String = "nn",
        showsPostInfo: Bool = true,
        width: CGFloat? = nil,
        @ViewBuilder thumbnail: () -> Thumbnail
    ) {
        self.size = size
        self.title = title
        self.captions = captions
        self.badges = badges
        self.dateText = dateText
        self.nickname = nickname
        self.likeText = likeText
        self.commentText = commentText
        self.showsPostInfo = showsPostInfo
        self.width = width
        self.thumbnail = thumbnail()
    }

    var body: some View {
        content
            .padding(size.padding)
            .frame(width: resolvedWidth, height: size.canvasSize.height)
            .frame(maxWidth: fillsAvailableWidth ? .infinity : nil)
            .background(Color.Object.default)
            .overlay(RoundedRectangle(cornerRadius: size.cornerRadius).stroke(Color.Border.default, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: size.cornerRadius))
    }

    @ViewBuilder private var content: some View {
        switch size {
        case .xsmall: xsmallLayout
        case .small: smallLayout
        case .medium: mediumLayout
        case .large: largeLayout
        }
    }

    private var resolvedWidth: CGFloat? {
        switch size {
        case .xsmall, .medium: width ?? size.canvasSize.width
        case .small, .large: width
        }
    }

    private var fillsAvailableWidth: Bool {
        switch size {
        case .xsmall, .medium: false
        case .small, .large: width == nil
        }
    }

    // MARK: - XSmall

    private var xsmallLayout: some View {
        VStack(alignment: .leading, spacing: 12) {
            imageArea(height: 84, overlayHeight: 35, badgePadding: nil, dateInset: 8)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(1)

                HStack(spacing: 2) {
                    ForEach(Array(captions.prefix(2).enumerated()), id: \.offset) { index, caption in
                        if index > 0 {
                            Text("·")
                        }
                        Text(caption)
                    }
                }
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
                .lineLimit(1)
            }
        }
    }

    // MARK: - Small

    private var smallLayout: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                badgeRow(size: .medium, maxCount: 2)
                Spacer(minLength: 8)
                Text(dateText)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
            }
            .frame(height: 32)

            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .appTypography(.titleLargeEmphasized)
                        .foregroundStyle(Color.Text.subtle)
                        .lineLimit(1)
                    if let caption = captions.first {
                        Text(caption)
                            .appTypography(.bodyLarge)
                            .foregroundStyle(Color.Text.muted)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)

                media
                    .frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .frame(height: 96)
        }
    }

    // MARK: - Medium / Large

    private var mediumLayout: some View {
        VStack(alignment: .leading, spacing: 12) {
            imageArea(height: 126, overlayHeight: 48, badgePadding: 8, dateInset: 12)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(1)

                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(captions.prefix(2).enumerated()), id: \.offset) { _, caption in
                        Text(caption)
                            .appTypography(.bodyLarge)
                            .foregroundStyle(Color.Text.muted)
                            .lineLimit(1)
                    }
                }
            }
        }
    }

    private var largeLayout: some View {
        VStack(alignment: .leading, spacing: 16) {
            imageArea(height: 178, overlayHeight: 56, badgePadding: 10, dateInset: 12)

            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .appTypography(.headlineMedium)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(1)

                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(captions.prefix(2).enumerated()), id: \.offset) { _, caption in
                        Text(caption)
                            .appTypography(.titleMedium)
                            .foregroundStyle(Color.Text.muted)
                            .lineLimit(1)
                    }
                }
            }
        }
    }

    // MARK: - Shared views

    private func imageArea(
        height: CGFloat,
        overlayHeight: CGFloat,
        badgePadding: CGFloat?,
        dateInset: CGFloat
    ) -> some View {
        media
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(alignment: .top) {
                LinearGradient(
                    colors: [Color(hex: 0x343434).opacity(0.64), .clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: overlayHeight)
                .overlay {
                    if let badgePadding {
                        HStack {
                            if let badge = badges.first {
                                imageBadge(badge, horizontalPadding: badgePadding, height: badgePadding == 8 ? 28 : 32)
                            }
                            Spacer()
                            Text(dateText)
                                .appTypography(.labelMedium)
                                .foregroundStyle(Color.Text.inverse)
                        }
                        .padding(dateInset)
                    } else {
                        HStack {
                            Text(dateText)
                                .appTypography(.labelMedium)
                                .foregroundStyle(Color.Text.inverse)
                            Spacer()
                        }
                        .padding(dateInset)
                    }
                }
            }
    }

    @ViewBuilder private var media: some View {
        if let thumbnail {
            thumbnail
        } else {
            AppImagePlaceholder(cornerRadius: 0)
        }
    }

    private func imageBadge(_ label: String, horizontalPadding: CGFloat, height: CGFloat) -> some View {
        Text(label)
            .appTypography(.labelMedium)
            .foregroundStyle(Color.Text.subtle)
            .padding(.horizontal, horizontalPadding)
            .frame(height: height)
            .background(Color.Object.default)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func badgeRow(size badgeSize: AppBadge.Size, maxCount: Int) -> some View {
        HStack(spacing: 8) {
            ForEach(Array(badges.prefix(maxCount).enumerated()), id: \.offset) { _, badge in
                AppBadge(label: badge, size: badgeSize, style: .solidPastel, variant: .secondary)
            }
        }
    }
}

extension AppCard where Thumbnail == EmptyView {
    init(
        size: Size,
        title: String,
        captions: [String] = [],
        badges: [String] = [],
        dateText: String = "mm/dd",
        nickname: String = "닉네임",
        likeText: String = "nn",
        commentText: String = "nn",
        showsPostInfo: Bool = true,
        width: CGFloat? = nil
    ) {
        self.size = size
        self.title = title
        self.captions = captions
        self.badges = badges
        self.dateText = dateText
        self.nickname = nickname
        self.likeText = likeText
        self.commentText = commentText
        self.showsPostInfo = showsPostInfo
        self.width = width
        self.thumbnail = nil
    }
}

#Preview {
    ScrollView {
        VStack(spacing: Spacing.lg) {
            AppCard(size: .xsmall, title: "타이틀", captions: ["캡션", "캡션"])
            AppCard(size: .small, title: "비료 주기", captions: ["캡션"], badges: ["레이블", "레이블"])
            AppCard(size: .medium, title: "타이틀", captions: ["캡션", "캡션"], badges: ["레이블"])
            AppCard(size: .large, title: "타이틀", captions: ["캡션", "캡션"], badges: ["레이블"])
        }
        .padding()
        .background(Color.Background.subtle)
    }
}
