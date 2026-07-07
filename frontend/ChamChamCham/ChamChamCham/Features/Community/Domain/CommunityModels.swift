//
//  CommunityModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Post kind. Mirrors the backend `CommunityPostType`. `QUESTION` posts are the ones eligible for answer
/// acceptance on the backend — that endpoint isn't deployed yet, so it's not modelled here.
enum CommunityPostType: String, Sendable, Hashable, CaseIterable {
    case general = "GENERAL"
    case question = "QUESTION"
}

/// List ordering. Mirrors the backend `CommunityPostSort`. `LATEST` is the server default.
enum CommunityPostSort: String, Sendable, Hashable, CaseIterable {
    case latest = "LATEST"
    case like = "LIKE"
    case comment = "COMMENT"
    case popular = "POPULAR"
}

/// A crop-scoped board. The `/boards` endpoint returns one entry per crop the member follows; the community
/// list is then filtered by `cropId`.
struct CommunityBoard: Identifiable, Hashable, Sendable {
    var id: UUID { cropId }
    let cropId: UUID
    let cropName: String
}

/// Post/comment author summary. `nickname`/`profileImageUrl` are optional because a member may not have set
/// them (or the account was removed).
struct CommunityAuthor: Hashable, Sendable {
    let memberId: UUID
    let nickname: String?
    let profileImageUrl: String?
}

/// A row in the community list. `bodyPreview`/`thumbnailUrl` are the list-optimized projection the backend
/// sends for `PostSummaryResponse` — the full body and image set only arrive with `CommunityPostDetail`.
struct CommunityPostSummary: Identifiable, Hashable, Sendable {
    let id: UUID
    let cropId: UUID
    let cropName: String
    let postType: CommunityPostType
    let title: String
    let bodyPreview: String
    let thumbnailUrl: String?
    let author: CommunityAuthor
    let commentCount: Int
    let likeCount: Int
    let likedByMe: Bool
    let createdAt: Date
}

/// Full post detail. `farmingRecordId` is present when the post shares a 영농일지 (BR-COMMUNITY-002).
struct CommunityPostDetail: Identifiable, Hashable, Sendable {
    let id: UUID
    let cropId: UUID
    let cropName: String
    let postType: CommunityPostType
    let title: String
    let body: String
    let imageUrls: [String]
    let farmingRecordId: UUID?
    let author: CommunityAuthor
    let commentCount: Int
    let likeCount: Int
    let likedByMe: Bool
    let createdAt: Date
}

/// A comment or reply. Replies are nested (`parentCommentId` non-nil, one level as delivered by the backend
/// tree). A soft-deleted comment is kept in the tree with `isDeleted == true` so its replies still render.
struct CommunityComment: Identifiable, Hashable, Sendable {
    let id: UUID
    let parentCommentId: UUID?
    let author: CommunityAuthor
    let body: String
    let imageUrl: String?
    let isDeleted: Bool
    let createdAt: Date
    let replies: [CommunityComment]
}

/// One cursor page of posts. `nextCursor == nil` means there are no more pages.
struct CommunityPostPage: Sendable {
    let items: [CommunityPostSummary]
    let nextCursor: String?
}

/// One cursor page of root comments (each carrying its nested replies).
struct CommunityCommentPage: Sendable {
    let items: [CommunityComment]
    let nextCursor: String?
}

/// Result of a like toggle: the new state and the resulting count.
struct LikeToggleResult: Hashable, Sendable {
    let liked: Bool
    let likeCount: Int
}
