//
//  AppListItem.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `list` row. One component covering five sizes:
/// - `xsmall`: title only (used in onboarding crop selection).
/// - `small`: badges + title + caption, with a right-aligned 88pt thumbnail.
/// - `medium`: date + title + badges, with a right-aligned 96pt thumbnail.
/// - `large`: title + organization + label/value info rows (no image).
/// - `xlarge`: full-width top image + badge/date row + title/caption + profile & reactions footer.
///
/// All sizes have zero horizontal padding and a full-width bottom divider. Images use
/// ``AppImagePlaceholder`` unless a `thumbnail` is provided.
struct AppListItem<Thumbnail: View>: View {
    enum Size {
        case xsmall
        case small
        case medium
        case large
        case xlarge
    }

    let size: Size
    var title: String = "타이틀"
    var caption: String = "캡션"
    var badges: [String] = ["레이블", "레이블"]
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
        badges: [String] = ["레이블", "레이블"],
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
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, size == .xlarge ? 0 : Spacing.md)
            .padding(.bottom, size == .xlarge ? Spacing.lg : Spacing.md)
            .overlay(alignment: .bottom) {
                if showsDivider {
                    Rectangle().fill(Color.Border.default).frame(height: 1)
                }
            }
    }

    @ViewBuilder private var content: some View {
        switch size {
        case .xsmall: xsmallBody
        case .small: smallBody
        case .medium: mediumBody
        case .large: largeBody
        case .xlarge: xlargeBody
        }
    }

    // MARK: - Sizes

    private var xsmallBody: some View {
        Text(title)
            .appTypography(.titleMediumEmphasized)
            .foregroundStyle(Color.Text.default)
            .lineLimit(2)
            .minimumScaleFactor(0.9)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var smallBody: some View {
        HStack(alignment: .center, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                if !badges.isEmpty {
                    badgeRow(size: .small)
                }
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

            if showsImage {
                mediaView.frame(width: 88, height: 88)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private var mediumBody: some View {
        HStack(alignment: .center, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(dateText)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
                VStack(alignment: .leading, spacing: 6) {
                    Text(title)
                        .appTypography(.titleLargeEmphasized)
                        .foregroundStyle(Color.Text.default)
                        .lineLimit(1)
                    if !badges.isEmpty {
                        badgeRow(size: .medium)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if showsImage {
                mediaView.frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private var largeBody: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
                Text(organization)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(1)
            }

            HStack(alignment: .top, spacing: Spacing.md) {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    ForEach(infoRows.indices, id: \.self) { index in
                        Text(infoRows[index].label)
                            .lineLimit(1)
                    }
                }
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(width: 56, alignment: .leading)

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    ForEach(infoRows.indices, id: \.self) { index in
                        Text(infoRows[index].value)
                            .lineLimit(1)
                    }
                }
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.default)
            }
        }
    }

    private var xlargeBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            if showsImage {
                mediaView
                    .frame(height: 200)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            HStack(alignment: .top) {
                badgeRow(size: .small)
                Spacer(minLength: Spacing.md)
                Text(dateText)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.subtle)
            }

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(1)
                Text(caption)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(Color.Text.muted)
                    .lineLimit(1)
            }

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

    // MARK: - Helpers

    private func badgeRow(size badgeSize: AppBadge.Size) -> some View {
        HStack(spacing: badgeSize == .medium ? 6 : Spacing.xs) {
            ForEach(Array(badges.prefix(2).enumerated()), id: \.offset) { _, badge in
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

    @ViewBuilder private var mediaView: some View {
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
        badges: [String] = ["레이블", "레이블"],
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
            AppListItem(size: .medium, title: "타이틀", badges: ["레이블", "레이블"])
            AppListItem(
                size: .large,
                title: "타이틀",
                organization: "기관",
                infoRows: [("대상자", "캡션"), ("지원금액", "캡션"), ("접수기간", "캡션")]
            )
            AppListItem(size: .xlarge, title: "타이틀", caption: "캡션...", badges: ["레이블", "레이블"])
        }
        .padding(.horizontal, Spacing.lg)
    }
}
