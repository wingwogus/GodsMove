//
//  AppListItem.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `list` row. Supports text-only rows and rows with a thumbnail while preserving the same
/// title/date/badge structure.
struct AppListItem<Thumbnail: View>: View {
    let title: String
    var dateText: String = "mm/dd"
    var labels: [String] = ["레이블", "레이블"]
    var reservesThumbnailSpace: Bool = false
    var showsDivider: Bool = true

    private let thumbnail: Thumbnail?

    init(
        title: String,
        dateText: String = "mm/dd",
        labels: [String] = ["레이블", "레이블"],
        reservesThumbnailSpace: Bool = false,
        showsDivider: Bool = true,
        @ViewBuilder thumbnail: () -> Thumbnail
    ) {
        self.title = title
        self.dateText = dateText
        self.labels = labels
        self.reservesThumbnailSpace = reservesThumbnailSpace
        self.showsDivider = showsDivider
        self.thumbnail = thumbnail()
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: Spacing.md) {
                if let thumbnail {
                    thumbnail
                        .frame(width: 72, height: 72)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                } else if reservesThumbnailSpace {
                    Color.clear.frame(width: 72, height: 72)
                }

                VStack(alignment: .leading, spacing: Spacing.sm) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(title)
                            .appTypography(.titleMediumEmphasized)
                            .foregroundStyle(Color.Text.default)
                            .lineLimit(1)
                        Spacer(minLength: Spacing.md)
                        Text(dateText)
                            .appTypography(.bodyMedium)
                            .foregroundStyle(Color.Text.default)
                    }

                    HStack(spacing: Spacing.sm) {
                        ForEach(labels, id: \.self) { label in
                            AppBadge(label: label, size: .small, style: .solid, variant: .primary)
                        }
                    }
                }
            }
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.md)

            if showsDivider {
                Rectangle()
                    .fill(Color.Border.default)
                    .frame(height: 1)
                    .padding(.horizontal, Spacing.md)
            }
        }
        .background(Color.Object.default)
    }
}

extension AppListItem where Thumbnail == EmptyView {
    init(
        title: String,
        dateText: String = "mm/dd",
        labels: [String] = ["레이블", "레이블"],
        reservesThumbnailSpace: Bool = false,
        showsDivider: Bool = true
    ) {
        self.title = title
        self.dateText = dateText
        self.labels = labels
        self.reservesThumbnailSpace = reservesThumbnailSpace
        self.showsDivider = showsDivider
        self.thumbnail = nil
    }
}

/// Figma information list block for title, organization, and label/value metadata.
struct AppInfoListItem: View {
    let title: String
    var organization: String
    var rows: [(label: String, value: String)]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(2)

                Text(organization)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.subtle)
                    .lineLimit(1)
            }

            VStack(alignment: .leading, spacing: Spacing.sm) {
                ForEach(rows.indices, id: \.self) { index in
                    let row = rows[index]
                    HStack(alignment: .firstTextBaseline, spacing: Spacing.lg) {
                        Text(row.label)
                            .appTypography(.bodyMedium)
                            .foregroundStyle(Color.Text.subtle)
                            .frame(width: 72, alignment: .leading)
                        Text(row.value)
                            .appTypography(.bodyMediumEmphasized)
                            .foregroundStyle(Color.Text.default)
                            .lineLimit(1)
                    }
                }
            }
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.default)
    }
}

#Preview {
    VStack(spacing: 0) {
        AppListItem(title: "타이틀", reservesThumbnailSpace: true)
        AppListItem(title: "타이틀") {
            AppImagePlaceholder(cornerRadius: 8, squareSize: 12)
        }
        AppInfoListItem(
            title: "타이틀",
            organization: "기관",
            rows: [
                ("대상자", "캡션"),
                ("지원금액", "캡션"),
                ("접수기간", "캡션")
            ]
        )
    }
    .padding()
    .background(Color.Background.subtle)
}
