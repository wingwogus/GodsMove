//
//  CommunityRequestDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Body for `POST`/`PATCH /api/v1/community/posts[/{id}]` — the backend uses a single `SavePostRequest` for
/// both create and update. `title` (≤50 chars) and `body` are required server-side; `farmingRecordId` links a
/// shared 영농일지 (BR-COMMUNITY-002); `mediaIds` (≤5) are pre-uploaded via `POST /api/v1/media/images`.
struct SavePostRequestDTO: Encodable, Sendable {
    let cropId: UUID
    let postType: String
    let title: String
    let body: String
    let farmingRecordId: UUID?
    let mediaIds: [UUID]
}

/// Body for `POST /api/v1/community/posts/{postId}/comments`. `parentCommentId == nil` is a root comment;
/// non-nil makes it a reply (BR-COMMUNITY-006/007). `mediaId` optionally attaches one pre-uploaded image.
struct CreateCommentRequestDTO: Encodable, Sendable {
    let parentCommentId: UUID?
    let body: String
    let mediaId: UUID?
}
