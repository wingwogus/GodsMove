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

    /// Flips true after a successful post deletion so the view can pop itself.
    private(set) var didDeletePost = false

    private var commentCursor: String?

    private let repository: any CommunityRepository

    init(postId: UUID, repository: any CommunityRepository) {
        self.postId = postId
        self.repository = repository
    }

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

    func submitComment() async {
        let body = draftComment.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty, !isSubmittingComment else { return }
        isSubmittingComment = true
        defer { isSubmittingComment = false }
        do {
            _ = try await repository.createComment(
                postId: postId,
                parentCommentId: replyTarget?.id,
                body: body,
                mediaId: nil
            )
            draftComment = ""
            replyTarget = nil
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
