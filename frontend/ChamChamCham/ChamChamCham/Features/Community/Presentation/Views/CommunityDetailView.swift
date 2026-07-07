//
//  CommunityDetailView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import SwiftUI

/// Post detail with the comment tree and a bottom composer. Pushed from the feed's `navigationDestination`.
struct CommunityDetailView: View {
    let postId: UUID
    private let container: DIContainer

    @State private var viewModel: CommunityDetailViewModel
    @FocusState private var commentFieldFocused: Bool
    @Environment(\.dismiss) private var dismiss

    init(postId: UUID, container: DIContainer) {
        self.postId = postId
        self.container = container
        _viewModel = State(initialValue: CommunityDetailViewModel(postId: postId, repository: container.makeCommunityRepository()))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            content
        }
        .navigationBarHidden(true)
        .safeAreaInset(edge: .bottom) { commentComposer }
        .task { await viewModel.load() }
        .onChange(of: viewModel.didDeletePost) { _, deleted in
            if deleted { dismiss() }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 44, height: 44)
            }
            Spacer()
            Text("게시글 상세")
                .appTypography(.bodyLargeEmphasized)
                .foregroundStyle(Color.Text.default)
            Spacer()
            Menu {
                Button(role: .destructive) {
                    Task { await viewModel.deletePost() }
                } label: {
                    Label("삭제", systemImage: "trash")
                }
                Button {} label: { Label("신고하기", systemImage: "exclamationmark.bubble") }
                    .disabled(true) // 신고 백엔드 미구현
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, Spacing.sm)
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        if viewModel.isLoading && viewModel.detail == nil {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let detail = viewModel.detail {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    postBody(detail)
                    Divider()
                    commentsSection
                }
                .padding(Spacing.md)
            }
        } else {
            VStack(spacing: Spacing.md) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 40))
                    .foregroundStyle(Color.Icon.disabled)
                Text(viewModel.errorMessage ?? "게시글을 불러오지 못했어요.")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func postBody(_ detail: CommunityPostDetail) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            CommunityAuthorLine(author: detail.author, createdAt: detail.createdAt, avatarSize: .small)

            Text(detail.title)
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.default)

            if !detail.imageUrls.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(detail.imageUrls, id: \.self) { url in
                            CommunityRemoteImage(url: url, cornerRadius: 12)
                                .frame(width: 280, height: 210)
                        }
                    }
                }
            }

            Text(detail.body)
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.default)

            HStack {
                CommunityTagRow(postType: detail.postType, cropName: detail.cropName)
                Spacer()
                CommunityMetrics(
                    likeCount: detail.likeCount,
                    likedByMe: detail.likedByMe,
                    commentCount: detail.commentCount,
                    onTapLike: { Task { await viewModel.toggleLike() } }
                )
            }
        }
    }

    // MARK: - Comments

    private var commentsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text("댓글 \(viewModel.detail?.commentCount ?? viewModel.comments.count)")
                .appTypography(.bodyLargeEmphasized)
                .foregroundStyle(Color.Text.default)

            if viewModel.comments.isEmpty {
                Text("첫 댓글을 남겨보세요.")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .padding(.vertical, Spacing.md)
            } else {
                ForEach(viewModel.comments) { comment in
                    CommentRow(comment: comment, depth: 0) { target in
                        viewModel.startReply(to: target)
                        commentFieldFocused = true
                    } onDelete: { target in
                        Task { await viewModel.deleteComment(target) }
                    }
                    .task { await viewModel.loadMoreCommentsIfNeeded(currentItem: comment) }
                }
                if viewModel.isLoadingMoreComments {
                    ProgressView().frame(maxWidth: .infinity)
                }
            }
        }
    }

    // MARK: - Composer

    private var commentComposer: some View {
        VStack(spacing: 0) {
            Divider()
            if let replyTarget = viewModel.replyTarget {
                HStack {
                    Text("\(replyTarget.author.nickname ?? "익명")님에게 답글 남기는 중")
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.muted)
                    Spacer()
                    Button("취소") { viewModel.cancelReply() }
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.primary)
                }
                .padding(.horizontal, Spacing.md)
                .padding(.top, Spacing.sm)
            }
            HStack(spacing: Spacing.sm) {
                TextField("댓글을 작성해주세요.", text: $viewModel.draftComment, axis: .vertical)
                    .appTypography(.bodyMedium)
                    .focused($commentFieldFocused)
                    .lineLimit(1...4)
                Button {
                    commentFieldFocused = false
                    Task { await viewModel.submitComment() }
                } label: {
                    if viewModel.isSubmittingComment {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.system(size: 30))
                            .foregroundStyle(canSubmitComment ? Color.Icon.primary : Color.Icon.disabled)
                    }
                }
                .disabled(!canSubmitComment)
            }
            .padding(Spacing.md)
        }
        .background(Color.Background.default)
    }

    private var canSubmitComment: Bool {
        !viewModel.draftComment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !viewModel.isSubmittingComment
    }
}

/// One comment and its nested replies. Replies indent once; deeper levels keep the same indent to stay
/// readable on a phone. A soft-deleted comment shows placeholder text but still renders its replies.
private struct CommentRow: View {
    let comment: CommunityComment
    let depth: Int
    let onReply: (CommunityComment) -> Void
    let onDelete: (CommunityComment) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                CommunityAvatar(profileImageUrl: comment.author.profileImageUrl, size: .small)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    HStack(spacing: Spacing.sm) {
                        Text(comment.author.nickname ?? "익명")
                            .appTypography(.labelMediumEmphasized)
                            .foregroundStyle(Color.Text.default)
                        Text(CommunityRelativeTime.string(from: comment.createdAt))
                            .appTypography(.labelMedium)
                            .foregroundStyle(Color.Text.muted)
                    }
                    Text(comment.isDeleted ? "삭제된 댓글입니다." : comment.body)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(comment.isDeleted ? Color.Text.muted : Color.Text.subtle)

                    if let imageUrl = comment.imageUrl, !comment.isDeleted {
                        CommunityRemoteImage(url: imageUrl)
                            .frame(width: 120, height: 120)
                    }

                    if !comment.isDeleted {
                        Button("댓글 달기") { onReply(comment) }
                            .appTypography(.labelMedium)
                            .foregroundStyle(Color.Text.muted)
                    }
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
            .contextMenu {
                if !comment.isDeleted {
                    Button(role: .destructive) { onDelete(comment) } label: {
                        Label("삭제", systemImage: "trash")
                    }
                }
            }

            ForEach(comment.replies) { reply in
                CommentRow(comment: reply, depth: depth + 1, onReply: onReply, onDelete: onDelete)
                    .padding(.leading, Spacing.xl)
            }
        }
    }
}
