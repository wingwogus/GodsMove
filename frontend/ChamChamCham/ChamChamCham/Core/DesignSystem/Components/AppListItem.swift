//
//  AppListItem.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// A single `AppListItem` badge with its own color treatment, so a row can mix a solid "type" tag
/// (e.g. Q&A) with a pastel category tag — Figma's `list` component doesn't style every badge the
/// same way. Plain string literals still work and keep today's default look (`solidPastel`/`secondary`).
struct AppListItemBadge: ExpressibleByStringLiteral {
    let label: String
    var style: AppBadge.Style = .solidPastel
    var variant: AppBadge.Variant = .secondary

    init(_ label: String, style: AppBadge.Style = .solidPastel, variant: AppBadge.Variant = .secondary) {
        self.label = label
        self.style = style
        self.variant = variant
    }

    init(stringLiteral value: String) {
        self.init(value)
    }
}

/// Figma `list` row. `large` is the new 120-point thumbnail layout; `xlarge` is the policy
/// information layout retained from the prior large component.
struct AppListItem<Thumbnail: View>: View {
    enum Size {
        case xsmall
        case small
        case medium
        case large
        case xlarge

        var canvasSize: CGSize {
            switch self {
            case .xsmall: CGSize(width: 390, height: 58)
            case .small: CGSize(width: 390, height: 120)
            case .medium: CGSize(width: 390, height: 160)
            case .large: CGSize(width: 390, height: 184)
            case .xlarge: CGSize(width: 390, height: 166)
            }
        }

        var thumbnailSide: CGFloat? {
            switch self {
            case .small: 88
            case .medium: 96
            case .large: 120
            case .xsmall, .xlarge: nil
            }
        }
    }

    let size: Size
    var title: String = "타이틀"
    var caption: String = "캡션"
    var badges: [AppListItemBadge] = ["레이블", "레이블"]
    var dateText: String = "mm/dd"
    var organization: String = "기관"
    var infoRows: [(label: String, value: String)] = []
    var nickname: String = "닉네임"
    var likeText: String = "nn"
    var commentText: String = "nn"
    var showsImage: Bool = true
    var showsDivider: Bool = true

    private let thumbnail: Thumbnail?

    init(
        size: Size,
        title: String = "타이틀",
        caption: String = "캡션",
        badges: [AppListItemBadge] = ["레이블", "레이블"],
        dateText: String = "mm/dd",
        organization: String = "기관",
        infoRows: [(label: String, value: String)] = [],
        nickname: String = "닉네임",
        likeText: String = "nn",
        commentText: String = "nn",
        showsImage: Bool = true,
        showsDivider: Bool = true,
        @ViewBuilder thumbnail: () -> Thumbnail
    ) {
        self.size = size
        self.title = title
        self.caption = caption
        self.badges = badges
        self.dateText = dateText
        self.organization = organization
        self.infoRows = infoRows
        self.nickname = nickname
        self.likeText = likeText
        self.commentText = commentText
        self.showsImage = showsImage
        self.showsDivider = showsDivider
        self.thumbnail = thumbnail()
    }

    var body: some View {
        content
            .padding(.horizontal, horizontalPadding)
            .frame(
                maxWidth: .infinity,
                minHeight: size.canvasSize.height,
                maxHeight: size.canvasSize.height,
                alignment: verticalAlignment
            )
            .overlay(alignment: .bottom) {
                if showsDivider {
                    Rectangle().fill(Color.Border.default).frame(height: 1)
                }
            }
    }

    /// How the (shorter-than-row) content sits inside the fixed-height row.
    /// `medium`/`large`/`xlarge` pin content to the top so the leftover height pools as a single
    /// 20pt bottom gap (Figma: 0 top / 20 bottom). `xsmall`/`small` center it (Figma: even 16/16),
    /// which also keeps every row identical when stacked in a `ForEach`.
    private var verticalAlignment: Alignment {
        switch size {
        case .xsmall, .small: .leading
        case .medium, .large, .xlarge: .topLeading
        }
    }

    private var horizontalPadding: CGFloat {
        switch size {
        case .small: 16
        case .xsmall, .medium, .large, .xlarge: 20
        }
    }

    @ViewBuilder private var content: some View {
        switch size {
        case .xsmall: xsmallBody
        case .small: smallBody
        case .medium: mediaBody(thumbnailSide: 96, titleFont: .titleMediumEmphasized, reactions: true)
        case .large: mediaBody(thumbnailSide: 120, titleFont: .titleLargeEmphasized, reactions: false)
        case .xlarge: xlargeBody
        }
    }

    private var xsmallBody: some View {
        Text(title)
            .appTypography(.titleMediumEmphasized)
            .foregroundStyle(Color.Text.subtle)
            .lineLimit(1)
            .frame(maxWidth: .infinity, minHeight: size.canvasSize.height, alignment: .leading)
    }

    private var smallBody: some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                badgeRow(size: .small)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .appTypography(.titleMediumEmphasized)
                        .foregroundStyle(Color.Text.subtle)
                        .lineLimit(1)
                    Text(caption)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.muted)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            thumbnail(side: 88)
        }
        .frame(height: 88)
        .padding(.vertical, 16)
    }

    private func mediaBody(thumbnailSide: CGFloat, titleFont: AppTypography, reactions: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                badgeRow(size: .medium)
                Spacer(minLength: 8)
                Text(dateText)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
            }
            .frame(height: 32)

            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(title)
                        .appTypography(titleFont)
                        .foregroundStyle(Color.Text.subtle)
                        .lineLimit(1)

                    Text(caption)
                        .appTypography(.bodyLarge)
                        .foregroundStyle(Color.Text.muted)
                        .padding(.top, 2)
                        .lineLimit(3)

                    if reactions {
                        Spacer(minLength: 0)
                        HStack(spacing: 12) {
                            reaction(icon: .asset("favorite_line"), text: likeText)
                            reaction(icon: .asset("chat_bubble_line"), text: commentText)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: thumbnailSide, alignment: .topLeading)

                thumbnail(side: thumbnailSide)
            }
            .frame(height: thumbnailSide)
        }
        // Sized naturally; `body`'s outer frame top-aligns it so the leftover height becomes a
        // single 20pt bottom gap (Figma medium/large: 0 top / 20 bottom).
    }

    private var xlargeBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
                Text(organization)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(1)
            }
            .frame(height: 52, alignment: .top)

            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(infoRows.indices, id: \.self) { index in
                        Text(infoRows[index].label)
                            .lineLimit(1)
                    }
                }
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(width: 56, alignment: .leading)

                VStack(alignment: .leading, spacing: 4) {
                    ForEach(infoRows.indices, id: \.self) { index in
                        Text(infoRows[index].value)
                            .lineLimit(1)
                    }
                }
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.default)
            }
        }
        .padding(.top, 20)
        // Same reasoning as `mediaBody`: natural height, top-aligned by `body`'s outer frame
        // (Figma xlarge: 0 top / 20 bottom).
    }

    private func badgeRow(size badgeSize: AppBadge.Size) -> some View {
        HStack(spacing: 8) {
            ForEach(Array(badges.prefix(2).enumerated()), id: \.offset) { _, badge in
                AppBadge(label: badge.label, size: badgeSize, style: badge.style, variant: badge.variant)
            }
        }
    }

    @ViewBuilder private func thumbnail(side: CGFloat) -> some View {
        if showsImage {
            media
                .frame(width: side, height: side)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private func reaction(icon: AppIconSource, text: String) -> some View {
        HStack(spacing: 2) {
            AppIconView(source: icon, size: 24)
                .foregroundStyle(Color.Icon.disabled)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
        }
    }

    @ViewBuilder private var media: some View {
        if let thumbnail {
            thumbnail
        } else {
            AppImagePlaceholder(cornerRadius: 0)
        }
    }
}

extension AppListItem where Thumbnail == EmptyView {
    init(
        size: Size,
        title: String = "타이틀",
        caption: String = "캡션",
        badges: [AppListItemBadge] = ["레이블", "레이블"],
        dateText: String = "mm/dd",
        organization: String = "기관",
        infoRows: [(label: String, value: String)] = [],
        nickname: String = "닉네임",
        likeText: String = "nn",
        commentText: String = "nn",
        showsImage: Bool = true,
        showsDivider: Bool = true
    ) {
        self.size = size
        self.title = title
        self.caption = caption
        self.badges = badges
        self.dateText = dateText
        self.organization = organization
        self.infoRows = infoRows
        self.nickname = nickname
        self.likeText = likeText
        self.commentText = commentText
        self.showsImage = showsImage
        self.showsDivider = showsDivider
        self.thumbnail = nil
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 0) {
            AppListItem(size: .xsmall, title: "타이틀")
            AppListItem(size: .small, title: "타이틀", caption: "캡션", badges: ["레이블", "레이블"])
            AppListItem(size: .medium, title: "타이틀", caption: "캡션", badges: ["레이블", "레이블"])
            AppListItem(size: .large, title: "타이틀", caption: "캡션", badges: ["레이블", "레이블"])
            AppListItem(
                size: .xlarge,
                title: "타이틀",
                organization: "기관",
                infoRows: [("대상자", "캡션"), ("지원금액", "캡션"), ("접수기간", "캡션")]
            )
        }
        .padding(.horizontal, 20)
    }
}
