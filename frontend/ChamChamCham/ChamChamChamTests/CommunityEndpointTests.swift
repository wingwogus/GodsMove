//
//  CommunityEndpointTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("CommunityEndpoint")
struct CommunityEndpointTests {

    private func queryDictionary(_ endpoint: CommunityEndpoint) -> [String: String] {
        Dictionary(uniqueKeysWithValues: endpoint.queryItems.map { ($0.name, $0.value ?? "") })
    }

    @Test("post/comment paths and HTTP methods match the backend contract")
    func pathsAndMethods() {
        let postId = UUID()
        let commentId = UUID()

        #expect(CommunityEndpoint.listBoards.path == "api/v1/community/boards")
        #expect(CommunityEndpoint.listBoards.method == .get)

        #expect(CommunityEndpoint.listPosts(CommunityPostQuery()).path == "api/v1/community/posts")
        #expect(CommunityEndpoint.getPost(postId).path == "api/v1/community/posts/\(postId.uuidString)")
        #expect(CommunityEndpoint.toggleLike(postId: postId).path
            == "api/v1/community/posts/\(postId.uuidString)/like-toggle")
        #expect(CommunityEndpoint.toggleLike(postId: postId).method == .post)
        #expect(CommunityEndpoint.deleteComment(commentId).path
            == "api/v1/community/comments/\(commentId.uuidString)")
        #expect(CommunityEndpoint.deleteComment(commentId).method == .delete)

        #expect(CommunityEndpoint.listComments(postId: postId, cursor: nil, size: 20).path
            == "api/v1/community/posts/\(postId.uuidString)/comments")
    }

    @Test("updatePost uses PATCH")
    func updateUsesPatch() {
        let dto = SavePostRequestDTO(
            cropId: UUID(), postType: "GENERAL", title: "t", body: "b", farmingRecordId: nil, mediaIds: []
        )
        #expect(CommunityEndpoint.updatePost(UUID(), dto).method == .patch)
        #expect(CommunityEndpoint.createPost(dto).method == .post)
    }

    @Test("write endpoints carry a body, read endpoints do not")
    func bodyPresence() {
        let dto = SavePostRequestDTO(
            cropId: UUID(), postType: "QUESTION", title: "t", body: "b", farmingRecordId: nil, mediaIds: []
        )
        #expect(CommunityEndpoint.createPost(dto).body != nil)
        #expect(CommunityEndpoint.updatePost(UUID(), dto).body != nil)
        #expect(CommunityEndpoint.createComment(
            postId: UUID(),
            CreateCommentRequestDTO(parentCommentId: nil, body: "b", mediaId: nil)
        ).body != nil)

        #expect(CommunityEndpoint.listBoards.body == nil)
        #expect(CommunityEndpoint.listPosts(CommunityPostQuery()).body == nil)
        #expect(CommunityEndpoint.getPost(UUID()).body == nil)
        #expect(CommunityEndpoint.deletePost(UUID()).body == nil)
        #expect(CommunityEndpoint.toggleLike(postId: UUID()).body == nil)
    }

    @Test("default post query sends only sort and size")
    func defaultPostQuery() {
        let query = queryDictionary(.listPosts(CommunityPostQuery()))
        #expect(query == ["sort": "LATEST", "size": "20"])
    }

    @Test("post query serializes every provided filter")
    func fullPostQuery() {
        let cropId = UUID()
        let endpoint = CommunityEndpoint.listPosts(
            CommunityPostQuery(
                cropId: cropId,
                postType: .question,
                keyword: "발아율",
                likedOnly: true,
                mineOnly: true,
                sort: .popular,
                cursor: "abc123",
                size: 30
            )
        )
        let query = queryDictionary(endpoint)

        #expect(query["cropId"] == cropId.uuidString)
        #expect(query["postType"] == "QUESTION")
        #expect(query["keyword"] == "발아율")
        #expect(query["likedOnly"] == "true")
        #expect(query["mineOnly"] == "true")
        #expect(query["sort"] == "POPULAR")
        #expect(query["cursor"] == "abc123")
        #expect(query["size"] == "30")
    }

    @Test("false flags and blank keyword are omitted from the query")
    func omitsDefaultsAndBlanks() {
        let query = queryDictionary(.listPosts(
            CommunityPostQuery(keyword: "   ", likedOnly: false, mineOnly: false)
        ))
        #expect(query["likedOnly"] == nil)
        #expect(query["mineOnly"] == nil)
        #expect(query["keyword"] == nil)
    }

    @Test("comment listing includes cursor only when present")
    func commentQuery() {
        let postId = UUID()
        let withoutCursor = queryDictionary(.listComments(postId: postId, cursor: nil, size: 20))
        #expect(withoutCursor == ["size": "20"])

        let withCursor = queryDictionary(.listComments(postId: postId, cursor: "next", size: 15))
        #expect(withCursor == ["size": "15", "cursor": "next"])
    }
}
