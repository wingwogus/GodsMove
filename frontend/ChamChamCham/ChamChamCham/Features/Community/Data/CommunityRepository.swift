//
//  CommunityRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// The single async entry point the community presentation layer talks to. Returns domain types (not DTOs),
/// mirroring `CropCatalogService`. Write calls return the new resource id; the caller re-fetches for fresh state.
protocol CommunityRepository: Sendable {
    func fetchBoards() async throws -> [CommunityBoard]
    /// Distinct crops referenced in `memberId`'s posts, including crops they no longer currently
    /// farm — backs the profile "기타 작물" filter section.
    func fetchPostCrops(memberId: UUID) async throws -> [CommunityBoard]
    func fetchPosts(_ query: CommunityPostQuery) async throws -> CommunityPostPage
    func fetchPostDetail(id: UUID) async throws -> CommunityPostDetail

    @discardableResult
    func createPost(
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID

    @discardableResult
    func updatePost(
        id: UUID,
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID

    func deletePost(id: UUID) async throws

    func fetchComments(postId: UUID, cursor: String?, size: Int) async throws -> CommunityCommentPage

    @discardableResult
    func createComment(
        postId: UUID,
        parentCommentId: UUID?,
        body: String,
        mediaId: UUID?
    ) async throws -> UUID

    func deleteComment(id: UUID) async throws

    @discardableResult
    func toggleLike(postId: UUID) async throws -> LikeToggleResult
}

struct RemoteCommunityRepository: CommunityRepository {
    let apiClient: APIClient

    func fetchBoards() async throws -> [CommunityBoard] {
        let dtos: [BoardResponseDTO] = try await apiClient.send(CommunityEndpoint.listBoards)
        return dtos.map { $0.toDomain() }
    }

    func fetchPostCrops(memberId: UUID) async throws -> [CommunityBoard] {
        let dtos: [BoardResponseDTO] = try await apiClient.send(CommunityEndpoint.postCrops(memberId: memberId))
        return dtos.map { $0.toDomain() }
    }

    func fetchPosts(_ query: CommunityPostQuery) async throws -> CommunityPostPage {
        let dto: PostPageResponseDTO = try await apiClient.send(CommunityEndpoint.listPosts(query))
        return dto.toDomain()
    }

    func fetchPostDetail(id: UUID) async throws -> CommunityPostDetail {
        let dto: PostDetailResponseDTO = try await apiClient.send(CommunityEndpoint.getPost(id))
        return dto.toDomain()
    }

    @discardableResult
    func createPost(
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID {
        let request = SavePostRequestDTO(
            cropId: cropId,
            postType: postType.rawValue,
            title: title,
            body: body,
            farmingRecordId: farmingRecordId,
            mediaIds: mediaIds
        )
        let response: PostIdResponseDTO = try await apiClient.send(CommunityEndpoint.createPost(request))
        return response.id
    }

    @discardableResult
    func updatePost(
        id: UUID,
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID {
        let request = SavePostRequestDTO(
            cropId: cropId,
            postType: postType.rawValue,
            title: title,
            body: body,
            farmingRecordId: farmingRecordId,
            mediaIds: mediaIds
        )
        let response: PostIdResponseDTO = try await apiClient.send(CommunityEndpoint.updatePost(id, request))
        return response.id
    }

    func deletePost(id: UUID) async throws {
        _ = try await apiClient.send(CommunityEndpoint.deletePost(id)) as EmptyDTO
    }

    func fetchComments(postId: UUID, cursor: String?, size: Int) async throws -> CommunityCommentPage {
        let dto: CommentPageResponseDTO = try await apiClient.send(
            CommunityEndpoint.listComments(postId: postId, cursor: cursor, size: size)
        )
        return dto.toDomain()
    }

    @discardableResult
    func createComment(
        postId: UUID,
        parentCommentId: UUID?,
        body: String,
        mediaId: UUID?
    ) async throws -> UUID {
        let request = CreateCommentRequestDTO(parentCommentId: parentCommentId, body: body, mediaId: mediaId)
        let response: CommentIdResponseDTO = try await apiClient.send(
            CommunityEndpoint.createComment(postId: postId, request)
        )
        return response.id
    }

    func deleteComment(id: UUID) async throws {
        _ = try await apiClient.send(CommunityEndpoint.deleteComment(id)) as EmptyDTO
    }

    @discardableResult
    func toggleLike(postId: UUID) async throws -> LikeToggleResult {
        let dto: LikeToggleResponseDTO = try await apiClient.send(CommunityEndpoint.toggleLike(postId: postId))
        return dto.toDomain()
    }
}
