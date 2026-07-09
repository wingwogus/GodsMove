//
//  AppContentCards.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `card` image variant: media area with an overlaid label and a text body.
struct AppImageCard<Thumbnail: View>: View {
    let label: String
    let title: String
    var caption: String?
    var secondaryCaption: String?
    var imageHeight: CGFloat = 142

    private let thumbnail: Thumbnail?

    init(
        label: String,
        title: String,
        caption: String? = nil,
        secondaryCaption: String? = nil,
        imageHeight: CGFloat = 142,
        @ViewBuilder thumbnail: () -> Thumbnail
    ) {
        self.label = label
        self.title = title
        self.caption = caption
        self.secondaryCaption = secondaryCaption
        self.imageHeight = imageHeight
        self.thumbnail = thumbnail()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                media
                    .frame(height: imageHeight)
                floatingLabel
                    .padding(.top, Spacing.lg)
                    .padding(.leading, Spacing.md)
            }

            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(2)

                if let caption {
                    Text(caption)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.default)
                        .lineLimit(2)
                }

                if let secondaryCaption {
                    Text(secondaryCaption)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.default)
                        .lineLimit(2)
                }
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.Object.default)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder private var media: some View {
        if let thumbnail {
            thumbnail
        } else {
            AppImagePlaceholder(cornerRadius: 0)
        }
    }

    private var floatingLabel: some View {
        Text(label)
            .appTypography(.labelMedium)
            .foregroundStyle(Color.Text.default)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Color.Object.default)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

extension AppImageCard where Thumbnail == EmptyView {
    init(
        label: String,
        title: String,
        caption: String? = nil,
        secondaryCaption: String? = nil,
        imageHeight: CGFloat = 142
    ) {
        self.label = label
        self.title = title
        self.caption = caption
        self.secondaryCaption = secondaryCaption
        self.imageHeight = imageHeight
        self.thumbnail = nil
    }
}

/// Figma `card` post variant: large media, labels/date, title, caption, and social footer.
struct AppPostCard<Thumbnail: View>: View {
    let title: String
    var caption: String
    var dateText: String = "mm/dd"
    var labels: [String] = ["레이블", "레이블"]
    var nickname: String = "닉네임"
    var likeText: String = "nn"
    var commentText: String = "nn"

    private let thumbnail: Thumbnail?

    init(
        title: String,
        caption: String,
        dateText: String = "mm/dd",
        labels: [String] = ["레이블", "레이블"],
        nickname: String = "닉네임",
        likeText: String = "nn",
        commentText: String = "nn",
        @ViewBuilder thumbnail: () -> Thumbnail
    ) {
        self.title = title
        self.caption = caption
        self.dateText = dateText
        self.labels = labels
        self.nickname = nickname
        self.likeText = likeText
        self.commentText = commentText
        self.thumbnail = thumbnail()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            media
                .frame(height: 190)

            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .center, spacing: Spacing.sm) {
                    ForEach(labels, id: \.self) { label in
                        AppBadge(label: label, size: .small, style: .solid, variant: .primary)
                    }
                    Spacer(minLength: Spacing.md)
                    Text(dateText)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.default)
                }

                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(2)

                Text(caption)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(2)

                HStack(spacing: Spacing.sm) {
                    AppAvatar(size: .xSmall) {
                        AppImagePlaceholder(isCircle: true, squareSize: 5)
                    }
                    Text(nickname)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.subtle)
                    Spacer()
                    footerMetric(systemImage: "heart", text: likeText)
                    footerMetric(systemImage: "bubble.left", text: commentText)
                }
            }
            .padding(Spacing.md)
            .background(Color.Object.default)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder private var media: some View {
        if let thumbnail {
            thumbnail
        } else {
            AppImagePlaceholder(cornerRadius: 0)
        }
    }

    private func footerMetric(systemImage: String, text: String) -> some View {
        HStack(spacing: 2) {
            Image(systemName: systemImage)
                .font(.system(size: 22))
                .foregroundStyle(Color.Icon.subtle)
            Text(text)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.subtle)
        }
    }
}

extension AppPostCard where Thumbnail == EmptyView {
    init(
        title: String,
        caption: String,
        dateText: String = "mm/dd",
        labels: [String] = ["레이블", "레이블"],
        nickname: String = "닉네임",
        likeText: String = "nn",
        commentText: String = "nn"
    ) {
        self.title = title
        self.caption = caption
        self.dateText = dateText
        self.labels = labels
        self.nickname = nickname
        self.likeText = likeText
        self.commentText = commentText
        self.thumbnail = nil
    }
}

/// Figma compact card variant with a leading label and two caption lines.
struct AppSummaryCard: View {
    let label: String
    var primaryCaption: String
    var secondaryCaption: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            AppBadge(label: label, size: .small, style: .solid, variant: .primary)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(primaryCaption)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
                Text(secondaryCaption)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
            }

            Spacer(minLength: 0)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.default)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

/// Figma CTA card variant with a badge, title, and circular arrow action.
struct AppActionCard: View {
    let label: String
    let title: String
    var action: () -> Void = {}

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                AppBadge(label: label, size: .small, style: .solid, variant: .primary)

                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(2)
            }

            Spacer(minLength: Spacing.md)

            AppButton(systemImage: "arrow.right", variant: .secondary, size: .small, action: action)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, minHeight: 76, alignment: .leading)
        .background(Color.Object.default)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

#Preview {
    ScrollView {
        VStack(spacing: Spacing.md) {
            AppImageCard(label: "레이블", title: "타이틀", caption: "캡션...", secondaryCaption: "캡션...")
                .frame(width: 258)

            AppPostCard(title: "타이틀", caption: "캡션...")

            AppSummaryCard(label: "레이블", primaryCaption: "캡션", secondaryCaption: "캡션")

            AppActionCard(label: "레이블", title: "타이틀")
        }
        .padding()
        .background(Color.Background.subtle)
    }
}
