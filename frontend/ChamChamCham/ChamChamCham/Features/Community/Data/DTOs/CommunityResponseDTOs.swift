//
//  CommunityResponseDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

// Wire-shape mirrors of the backend `CommunityResponses`. Each maps to a domain type in
// `CommunityRepository`. `createdAt` is decoded as `String` (matching the app's existing DTO convention — the
// shared `JSONDecoder` sets no date strategy) and parsed via `CommunityDateParser` during mapping. `postType`
// is a plain `String` so an unknown future server case can't break decoding; mapping resolves it.

struct BoardResponseDTO: Decodable, Sendable {
    let cropId: UUID
    let cropName: String
}

struct PostIdResponseDTO: Decodable, Sendable {
    let id: UUID
}

struct CommentIdResponseDTO: Decodable, Sendable {
    let id: UUID
}

struct LikeToggleResponseDTO: Decodable, Sendable {
    let liked: Bool
    let likeCount: Int
}

struct AuthorResponseDTO: Decodable, Sendable {
    let memberId: UUID
    let nickname: String?
    let profileImageUrl: String?
}

struct PostPageResponseDTO: Decodable, Sendable {
    let items: [PostSummaryResponseDTO]
    let nextCursor: String?
}

struct PostSummaryResponseDTO: Decodable, Sendable {
    let id: UUID
    let cropId: UUID
    let cropName: String
    let postType: String
    let title: String
    let bodyPreview: String
    let thumbnailUrl: String?
    let author: AuthorResponseDTO
    let commentCount: Int
    let likeCount: Int
    let likedByMe: Bool
    let createdAt: String
}

struct PostDetailResponseDTO: Decodable, Sendable {
    let id: UUID
    let cropId: UUID
    let cropName: String
    let postType: String
    let title: String
    let body: String
    let imageUrls: [String]
    let farmingRecordId: UUID?
    let author: AuthorResponseDTO
    let commentCount: Int
    let likeCount: Int
    let likedByMe: Bool
    let createdAt: String
}

struct CommentPageResponseDTO: Decodable, Sendable {
    let items: [CommentResponseDTO]
    let nextCursor: String?
}

struct CommentResponseDTO: Decodable, Sendable {
    let id: UUID
    let parentCommentId: UUID?
    let author: AuthorResponseDTO
    let body: String
    let imageUrl: String?
    let deleted: Bool
    let createdAt: String
    let replies: [CommentResponseDTO]
}

// MARK: - Domain mapping
//
// Kept in the Data layer (DTO → domain), so the repository just forwards. Unknown/new server `postType`
// values fall back to `.general` and unparseable timestamps to `.distantPast`, so one odd row can never fail
// decoding of a whole page.

extension AuthorResponseDTO {
    func toDomain() -> CommunityAuthor {
        CommunityAuthor(memberId: memberId, nickname: nickname, profileImageUrl: profileImageUrl)
    }
}

extension PostSummaryResponseDTO {
    func toDomain() -> CommunityPostSummary {
        CommunityPostSummary(
            id: id,
            cropId: cropId,
            cropName: cropName,
            postType: CommunityPostType(rawValue: postType) ?? .general,
            title: title,
            bodyPreview: bodyPreview,
            thumbnailUrl: thumbnailUrl,
            author: author.toDomain(),
            commentCount: commentCount,
            likeCount: likeCount,
            likedByMe: likedByMe,
            createdAt: CommunityDateParser.date(from: createdAt)
        )
    }
}

extension PostPageResponseDTO {
    func toDomain() -> CommunityPostPage {
        CommunityPostPage(items: items.map { $0.toDomain() }, nextCursor: nextCursor)
    }
}

extension PostDetailResponseDTO {
    func toDomain() -> CommunityPostDetail {
        CommunityPostDetail(
            id: id,
            cropId: cropId,
            cropName: cropName,
            postType: CommunityPostType(rawValue: postType) ?? .general,
            title: title,
            body: body,
            imageUrls: imageUrls,
            farmingRecordId: farmingRecordId,
            author: author.toDomain(),
            commentCount: commentCount,
            likeCount: likeCount,
            likedByMe: likedByMe,
            createdAt: CommunityDateParser.date(from: createdAt)
        )
    }
}

extension CommentResponseDTO {
    func toDomain() -> CommunityComment {
        CommunityComment(
            id: id,
            parentCommentId: parentCommentId,
            author: author.toDomain(),
            body: body,
            imageUrl: imageUrl,
            isDeleted: deleted,
            createdAt: CommunityDateParser.date(from: createdAt),
            replies: replies.map { $0.toDomain() }
        )
    }
}

extension CommentPageResponseDTO {
    func toDomain() -> CommunityCommentPage {
        CommunityCommentPage(items: items.map { $0.toDomain() }, nextCursor: nextCursor)
    }
}

extension BoardResponseDTO {
    func toDomain() -> CommunityBoard {
        CommunityBoard(cropId: cropId, cropName: cropName)
    }
}

extension LikeToggleResponseDTO {
    func toDomain() -> LikeToggleResult {
        LikeToggleResult(liked: liked, likeCount: likeCount)
    }
}
