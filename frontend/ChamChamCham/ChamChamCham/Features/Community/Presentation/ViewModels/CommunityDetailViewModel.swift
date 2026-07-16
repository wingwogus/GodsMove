//
//  CommunityDetailViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Drives the post detail screen: the post body, its cursor-paged comment tree, like toggling, comment/reply
/// composition, and deletion (post + comment).
@MainActor
@Observable
final class CommunityDetailViewModel {
    /// The single image optionally attached to the next comment. `id` is a local temp id (stable while
    /// uploading); `mediaId` is filled once the upload succeeds and is what gets sent on submit.
    struct Attachment: Identifiable, Sendable {
        let id: UUID
        let previewData: Data
        var mediaId: UUID?
        var isUploading: Bool
    }

    let postId: UUID

    private(set) var detail: CommunityPostDetail?
    private(set) var comments: [CommunityComment] = []
    private(set) var isLoading = false
    private(set) var isLoadingMoreComments = false
    private(set) var errorMessage: String?

    /// Draft of the composer at the bottom. `replyTarget` non-nil means the next submit is a reply to it.
    var draftComment: String = ""
    private(set) var replyTarget: CommunityComment?
    private(set) var isSubmittingComment = false

    /// A comment takes a single optional image. Uploaded as it's picked so submit only sends the `mediaId`.
    private(set) var attachment: Attachment?

    /// Flips true after a successful post deletion so the view can pop itself.
    private(set) var didDeletePost = false

    private var commentCursor: String?

    private let repository: any CommunityRepository
    private let mediaRepository: any MediaUploadRepository

    init(
        postId: UUID,
        repository: any CommunityRepository,
        mediaRepository: any MediaUploadRepository
    ) {
        self.postId = postId
        self.repository = repository
        self.mediaRepository = mediaRepository
    }

    /// True while the attached image is still uploading — submit waits for this so no `mediaId` is missed.
    var isUploadingImage: Bool { attachment?.isUploading == true }

    var hasMoreComments: Bool { commentCursor != nil }

    func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            async let detailTask = repository.fetchPostDetail(id: postId)
            async let commentsTask = repository.fetchComments(postId: postId, cursor: nil, size: 20)
            detail = try await detailTask
            let page = try await commentsTask
            comments = page.items
            commentCursor = page.nextCursor
        } catch {
            errorMessage = CommunityErrorMessage.text(for: error)
        }
    }

    func loadMoreCommentsIfNeeded(currentItem: CommunityComment) async {
        guard let cursor = commentCursor, !isLoadingMoreComments,
              currentItem.id == comments.last?.id else { return }
        isLoadingMoreComments = true
        defer { isLoadingMoreComments = false }
        guard let page = try? await repository.fetchComments(postId: postId, cursor: cursor, size: 20) else { return }
        comments.append(contentsOf: page.items)
        commentCursor = page.nextCursor
    }

    // MARK: - Comments

    func startReply(to comment: CommunityComment) {
        replyTarget = comment
    }

    func cancelReply() {
        replyTarget = nil
    }

    // MARK: - Image attachment

    func addImage(_ data: Data) async {
        // Show the picked image immediately with a spinner, then fill in the mediaId once uploaded.
        let tempId = UUID()
        attachment = Attachment(id: tempId, previewData: data, mediaId: nil, isUploading: true)
        do {
            let uploaded = try await mediaRepository.uploadCommunityImage(data, originalFilename: nil)
            guard attachment?.id == tempId else { return }
            attachment?.mediaId = uploaded.mediaId
            attachment?.isUploading = false
        } catch {
            if attachment?.id == tempId { attachment = nil }
            errorMessage = "사진을 올리지 못했어요. 다시 시도해주세요."
        }
    }

    func removeImage() {
        attachment = nil
    }

    func submitComment() async {
        let body = draftComment.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty, !isSubmittingComment, !isUploadingImage else { return }
        isSubmittingComment = true
        defer { isSubmittingComment = false }
        do {
            _ = try await repository.createComment(
                postId: postId,
                parentCommentId: replyTarget?.id,
                body: body,
                mediaId: attachment?.mediaId
            )
            draftComment = ""
            replyTarget = nil
            attachment = nil
            await reloadComments()
            detail?.commentCount += 1
        } catch {
            errorMessage = CommunityErrorMessage.text(for: error)
        }
    }

    func deleteComment(_ comment: CommunityComment) async {
        do {
            try await repository.deleteComment(id: comment.id)
            await reloadComments()
        } catch {
            errorMessage = CommunityErrorMessage.text(for: error)
        }
    }

    // MARK: - Post

    func toggleLike() async {
        guard let current = detail else { return }
        do {
            let result = try await repository.toggleLike(postId: current.id)
            detail?.likedByMe = result.liked
            detail?.likeCount = result.likeCount
        } catch {
            // Leave state unchanged on failure.
        }
    }

    func deletePost() async {
        do {
            try await repository.deletePost(id: postId)
            didDeletePost = true
        } catch {
            errorMessage = CommunityErrorMessage.text(for: error)
        }
    }

    /// Refetches the first comment page after a mutation so the tree (and its nesting) reflects the change.
    private func reloadComments() async {
        guard let page = try? await repository.fetchComments(postId: postId, cursor: nil, size: 20) else { return }
        comments = page.items
        commentCursor = page.nextCursor
    }
}
