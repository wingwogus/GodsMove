//
//  CommunityResponseDecodingTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Community response decoding + domain mapping")
struct CommunityResponseDecodingTests {

    private func decodeEnvelope<T: Decodable & Sendable>(_ type: T.Type, from json: String) throws -> T {
        let envelope = try JSONDecoder().decode(APIEnvelope<T>.self, from: Data(json.utf8))
        #expect(envelope.success)
        return try #require(envelope.data)
    }

    @Test("decodes a post page and maps it to domain summaries")
    func postPage() throws {
        let json = """
        {
          "success": true,
          "data": {
            "items": [
              {
                "id": "11111111-1111-1111-1111-111111111111",
                "cropId": "22222222-2222-2222-2222-222222222222",
                "cropName": "황기",
                "postType": "QUESTION",
                "title": "황기 발아율이 너무 낮은데",
                "bodyPreview": "올해 파종했는데 싹이...",
                "thumbnailUrl": "https://img/thumb.jpg",
                "author": {
                  "memberId": "33333333-3333-3333-3333-333333333333",
                  "nickname": "나루지기",
                  "profileImageUrl": null
                },
                "commentCount": 12,
                "likeCount": 8,
                "likedByMe": true,
                "createdAt": "2026-07-07T09:30:00"
              }
            ],
            "nextCursor": "cursor-abc"
          },
          "error": null
        }
        """

        let page = try decodeEnvelope(PostPageResponseDTO.self, from: json).toDomain()

        #expect(page.nextCursor == "cursor-abc")
        let post = try #require(page.items.first)
        #expect(post.title == "황기 발아율이 너무 낮은데")
        #expect(post.postType == .question)
        #expect(post.cropName == "황기")
        #expect(post.thumbnailUrl == "https://img/thumb.jpg")
        #expect(post.author.nickname == "나루지기")
        #expect(post.author.profileImageUrl == nil)
        #expect(post.commentCount == 12)
        #expect(post.likeCount == 8)
        #expect(post.likedByMe)
        #expect(post.createdAt != .distantPast)
    }

    @Test("unknown postType falls back to .general instead of failing")
    func unknownPostTypeFallsBack() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "cropId": "22222222-2222-2222-2222-222222222222",
          "cropName": "인삼",
          "postType": "ANNOUNCEMENT",
          "title": "t",
          "bodyPreview": "p",
          "thumbnailUrl": null,
          "author": { "memberId": "33333333-3333-3333-3333-333333333333", "nickname": null, "profileImageUrl": null },
          "commentCount": 0,
          "likeCount": 0,
          "likedByMe": false,
          "createdAt": "2026-07-07T09:30:00"
        }
        """
        let dto = try JSONDecoder().decode(PostSummaryResponseDTO.self, from: Data(json.utf8))
        #expect(dto.toDomain().postType == .general)
    }

    @Test("decodes a post detail with image list and shared farming record")
    func postDetail() throws {
        let json = """
        {
          "success": true,
          "data": {
            "id": "11111111-1111-1111-1111-111111111111",
            "cropId": "22222222-2222-2222-2222-222222222222",
            "cropName": "당귀",
            "postType": "GENERAL",
            "title": "당귀 수확 후 건조 방법",
            "body": "그늘에서 천천히 말리세요.",
            "imageUrls": ["https://img/1.jpg", "https://img/2.jpg"],
            "farmingRecordId": "44444444-4444-4444-4444-444444444444",
            "author": { "memberId": "33333333-3333-3333-3333-333333333333", "nickname": "당귀지기", "profileImageUrl": "https://img/p.jpg" },
            "commentCount": 3,
            "likeCount": 5,
            "likedByMe": false,
            "createdAt": "2026-07-07T12:00:00.250"
          },
          "error": null
        }
        """

        let detail = try decodeEnvelope(PostDetailResponseDTO.self, from: json).toDomain()

        #expect(detail.postType == .general)
        #expect(detail.imageUrls.count == 2)
        #expect(detail.farmingRecordId == UUID(uuidString: "44444444-4444-4444-4444-444444444444"))
        #expect(detail.author.profileImageUrl == "https://img/p.jpg")
        #expect(detail.createdAt != .distantPast)
    }

    @Test("decodes a nested comment tree and preserves the deleted flag")
    func commentTree() throws {
        let json = """
        {
          "success": true,
          "data": {
            "items": [
              {
                "id": "aaaaaaaa-0000-0000-0000-000000000001",
                "parentCommentId": null,
                "author": { "memberId": "33333333-3333-3333-3333-333333333333", "nickname": "질문자", "profileImageUrl": null },
                "body": "삭제된 댓글입니다",
                "imageUrl": null,
                "deleted": true,
                "createdAt": "2026-07-07T09:00:00",
                "replies": [
                  {
                    "id": "aaaaaaaa-0000-0000-0000-000000000002",
                    "parentCommentId": "aaaaaaaa-0000-0000-0000-000000000001",
                    "author": { "memberId": "55555555-5555-5555-5555-555555555555", "nickname": "답변자", "profileImageUrl": null },
                    "body": "이건 답글이에요",
                    "imageUrl": "https://img/c.jpg",
                    "deleted": false,
                    "createdAt": "2026-07-07T09:05:00",
                    "replies": []
                  }
                ]
              }
            ],
            "nextCursor": null
          },
          "error": null
        }
        """

        let page = try decodeEnvelope(CommentPageResponseDTO.self, from: json).toDomain()

        #expect(page.nextCursor == nil)
        let root = try #require(page.items.first)
        #expect(root.isDeleted)
        #expect(root.parentCommentId == nil)
        #expect(root.replies.count == 1)

        let reply = try #require(root.replies.first)
        #expect(!reply.isDeleted)
        #expect(reply.parentCommentId == root.id)
        #expect(reply.imageUrl == "https://img/c.jpg")
    }

    @Test("decodes Swagger comment page when replies key is absent")
    func commentPageWithoutReplies() throws {
        let json = """
        {
          "success": true,
          "data": {
            "items": [
              {
                "id": "aaaaaaaa-0000-0000-0000-000000000001",
                "parentCommentId": null,
                "author": {
                  "memberId": "33333333-3333-3333-3333-333333333333",
                  "nickname": "질문자",
                  "profileImageUrl": null
                },
                "body": "댓글입니다",
                "deleted": false,
                "createdAt": "2026-07-07T09:00:00"
              }
            ],
            "nextCursor": null
          },
          "error": null
        }
        """

        let page = try decodeEnvelope(CommentPageResponseDTO.self, from: json).toDomain()

        let comment = try #require(page.items.first)
        #expect(comment.replies.isEmpty)
    }

    @Test("decodes a like toggle result")
    func likeToggle() throws {
        let json = """
        { "success": true, "data": { "liked": true, "likeCount": 9 }, "error": null }
        """
        let result = try decodeEnvelope(LikeToggleResponseDTO.self, from: json).toDomain()
        #expect(result.liked)
        #expect(result.likeCount == 9)
    }

    @Test("decodes the board list")
    func boards() throws {
        let json = """
        {
          "success": true,
          "data": [
            { "cropId": "22222222-2222-2222-2222-222222222222", "cropName": "인삼" },
            { "cropId": "66666666-6666-6666-6666-666666666666", "cropName": "황기" }
          ],
          "error": null
        }
        """
        let boards = try decodeEnvelope([BoardResponseDTO].self, from: json).map { $0.toDomain() }
        #expect(boards.count == 2)
        #expect(boards.first?.cropName == "인삼")
    }
}
