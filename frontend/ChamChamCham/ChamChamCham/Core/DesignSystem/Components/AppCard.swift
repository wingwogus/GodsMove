//
//  AppCard.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import SwiftUI

/// Figma `card`. A reusable card with four sizes:
/// - `xsmall`: compact horizontal card — title/caption content with a right-aligned image.
/// - `small`: compact vertical card (default width 258) — image with an overlaid white badge,
///   title, and up to two caption lines.
/// - `medium`: full-width post card — image, a badge row + date, title, caption, and footer.
/// - `large`: expanded post card — larger image and slightly roomier text treatment.
///
/// Images use ``AppImagePlaceholder`` by default; pass a `thumbnail` for real media.
struct AppCard<Thumbnail: View>: View {
    enum Size {
        case xsmall
        case small
        case medium
        case large
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
    /// Overrides width. `small` defaults to 258; other sizes fill their container when omitted.
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
        Group {
            switch size {
            case .xsmall:
                xsmallContent
            case .small:
                smallLayout
            case .medium:
                postLayout(imageHeight: 178, titleLineLimit: 1, captionLineLimit: 1)
            case .large:
                postLayout(imageHeight: 220, titleLineLimit: 2, captionLineLimit: 2)
            }
        }
        .padding(cardPadding)
        .frame(minHeight: size == .xsmall ? 104 : nil)
        .frame(width: resolvedWidth)
        .frame(maxWidth: fillsAvailableWidth ? .infinity : nil)
        .background(Color.Object.default)

        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.Border.default, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var cardPadding: CGFloat {
        switch size {
        case .xsmall, .small: Spacing.md
        case .medium, .large: Spacing.lg
        }
    }

    private var resolvedWidth: CGFloat? {
        switch size {
        case .small:
            width ?? 258
        case .xsmall, .medium, .large:
            width
        }
    }

    private var fillsAvailableWidth: Bool {
        switch size {
        case .small:
            width != nil
        case .xsmall, .medium, .large:
            width == nil
        }
    }

    // MARK: - Image

    private var smallImageArea: some View {
        media
            .frame(height: 126)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(alignment: .topLeading) {
                if let badge = badges.first {
                    overlayBadge(badge)
                        .padding(Spacing.sm)
                }
            }
    }

    private func postImageArea(height: CGFloat) -> some View {
        media
            .frame(height: height)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func trailingImage(side: CGFloat) -> some View {
        media
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder private var media: some View {
        if let thumbnail {
            thumbnail
        } else {
            AppImagePlaceholder(cornerRadius: 0)
        }
    }

    private func overlayBadge(_ label: String) -> some View {
        Text(label)
            .appTypography(.labelMedium)
            .foregroundStyle(Color.Text.subtle)
            .padding(Spacing.sm)
            .frame(minWidth: 36, minHeight: 28)
            .background(Color.Object.default)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    // MARK: - XSmall

    private var xsmallContent: some View {
        HStack(alignment: .center, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                if !badges.isEmpty {
                    badgeRow(size: .small, maxCount: 1)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .appTypography(.titleMediumEmphasized)
                        .foregroundStyle(Color.Text.default)
                        .lineLimit(2)
                        .minimumScaleFactor(0.9)

                    if let caption = captions.first {
                        Text(caption)
                            .appTypography(.bodyMedium)
                            .foregroundStyle(Color.Text.muted)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            trailingImage(side: 72)
        }
    }

    // MARK: - Small

    private var smallLayout: some View {
        VStack(alignment: .leading, spacing: 12) {
            smallImageArea
            smallContent
        }
    }

    private var smallContent: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .appTypography(.titleLargeEmphasized)
                .foregroundStyle(Color.Text.subtle)
                .lineLimit(1)

            if !captions.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(captions.prefix(2).enumerated()), id: \.offset) { _, caption in
                        Text(caption)
                            .appTypography(.bodyLarge)
                            .foregroundStyle(Color.Text.subtle)
                            .lineLimit(1)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Medium / Large

    private func postLayout(
        imageHeight: CGFloat,
        titleLineLimit: Int,
        captionLineLimit: Int
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            postImageArea(height: imageHeight)
            postMetaRow

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(titleLineLimit)
                if let caption = captions.first {
                    Text(caption)
                        .appTypography(.bodyLarge)
                        .foregroundStyle(Color.Text.muted)
                        .lineLimit(captionLineLimit)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if showsPostInfo {
                HStack(spacing: Spacing.xs) {
                    HStack(spacing: Spacing.sm) {
                        AppAvatar(size: .small) {
                            AppImagePlaceholder(isCircle: true, squareSize: 6)
                        }
                        Text(nickname)
                            .appTypography(.bodyMedium)
                            .foregroundStyle(Color.Text.muted)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    HStack(spacing: 12) {
                        reaction(systemImage: "heart", text: likeText)
                        reaction(systemImage: "bubble.left", text: commentText)
                    }
                }
            }
        }
    }

    private var postMetaRow: some View {
        HStack(alignment: .top) {
            badgeRow(size: .small, maxCount: 2)
            Spacer(minLength: Spacing.md)
            Text(dateText)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.subtle)
        }
    }

    private func badgeRow(size badgeSize: AppBadge.Size, maxCount: Int) -> some View {
        HStack(spacing: Spacing.xs) {
            ForEach(Array(badges.prefix(maxCount).enumerated()), id: \.offset) { _, badge in
                AppBadge(label: badge, size: badgeSize, style: .solidPastel, variant: .secondary)
            }
        }
    }

    private func reaction(systemImage: String, text: String) -> some View {
        HStack(spacing: 2) {
            Image(systemName: systemImage)
                .font(.system(size: 22))
                .foregroundStyle(Color.Icon.subtle)
                .frame(width: 24, height: 24)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
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
            AppCard(
                size: .xsmall,
                title: "타이틀",
                captions: ["캡션"],
                badges: ["레이블"]
            )

            AppCard(
                size: .small,
                title: "타이틀",
                captions: ["캡션...", "캡션..."],
                badges: ["레이블"]
            )

            AppCard(
                size: .medium,
                title: "타이틀",
                captions: ["캡션..."],
                badges: ["레이블", "레이블"]
            )

            AppCard(
                size: .large,
                title: "타이틀",
                captions: ["캡션은 두 줄까지 표시할 수 있습니다."],
                badges: ["레이블", "레이블"]
            )
        }
        .padding()
        .background(Color.Background.subtle)
    }
}
