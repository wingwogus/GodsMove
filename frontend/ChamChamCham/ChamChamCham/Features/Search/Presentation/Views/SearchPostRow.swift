//
//  SearchPostRow.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// One row in the 게시글 search tab (Figma `list`, size `medium`). Unlike `CommunityPostRow`, this
/// has no inline like-toggle overlay — search results push straight to `CommunityDetailView`, where
/// liking already works, so there's no need to wire a second `toggleLike` path through 3 more search
/// view models just for this list.
struct SearchPostRow: View {
    let post: CommunityPostSummary
    var showsDivider: Bool = true

    var body: some View {
        AppListItem(
            size: .medium,
            title: post.title,
            caption: post.bodyPreview,
            badges: badges,
            dateText: rowDateText(post.createdAt),
            likeText: "\(post.likeCount)",
            commentText: "\(post.commentCount)",
            showsDivider: showsDivider
        ) {
            CommunityRemoteImage(url: post.thumbnailUrl)
        }
    }

    private var badges: [AppListItemBadge] {
        let category = AppListItemBadge(post.cropName, style: .solidPastel, variant: .primary)
        return post.postType == .question ? [category, AppListItemBadge("Q&A")] : [category]
    }

    private func rowDateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd"
        return formatter.string(from: date)
    }
}
