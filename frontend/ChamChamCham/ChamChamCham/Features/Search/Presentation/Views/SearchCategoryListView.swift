//
//  SearchCategoryListView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Shared full-list layout for the 3 single-category search tabs (나의 일지/정책 정보/게시글):
/// a "총 N개" count header bound to the real `totalCount` (never Figma's placeholder "총 12개"),
/// then a flush `LazyVStack` of rows where every row but the last shows `AppListItem`'s bottom
/// divider — Figma's own rule, which only the 나의 일지 탭 capture broke (a mockup duplication
/// slip, not a real spec). Generic over `Row: View` since each category's row construction (tap
/// destination, badges) differs too much to also share.
struct SearchCategoryListView<Item: Identifiable, Row: View>: View {
    let items: [Item]
    let totalCount: Int
    let isLoading: Bool
    let isLoadingMore: Bool
    let errorMessage: String?
    let emptyText: String
    @ViewBuilder let row: (Item, Bool) -> Row
    let onLoadMore: (Item) async -> Void
    let onRefresh: () async -> Void

    var body: some View {
        ScrollView {
            countHeader
            content
        }
        .refreshable { await onRefresh() }
    }

    private var countHeader: some View {
        Text("총 \(totalCount)개")
            .appTypography(.bodyMedium)
            .foregroundStyle(Color.Text.muted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 40)
            .padding(.horizontal, 20)
            .background(Color.Background.subtle)
    }

    @ViewBuilder private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.xl)
        } else if let errorMessage {
            emptyState(text: errorMessage)
        } else if items.isEmpty {
            emptyState(text: emptyText)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    row(item, index != items.count - 1)
                        .task { await onLoadMore(item) }
                }
                if isLoadingMore {
                    ProgressView().padding(Spacing.md)
                }
            }
            .padding(.bottom, 112)
        }
    }

    private func emptyState(text: String) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.disabled)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
    }
}
