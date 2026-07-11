//
//  CommunityDetailView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import SwiftData
import SwiftUI

/// Post detail with the comment tree and a bottom composer. Pushed from the feed's `navigationDestination`.
struct CommunityDetailView: View {
    let postId: UUID
    private let container: DIContainer
    private let horizontalInset: CGFloat = 20

    @State private var viewModel: CommunityDetailViewModel
    /// The logged-in member, read from the local cache — used to show delete only on the user's own content.
    @State private var currentMemberId: UUID?
    @FocusState private var commentFieldFocused: Bool
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    init(postId: UUID, container: DIContainer) {
        self.postId = postId
        self.container = container
        _viewModel = State(initialValue: CommunityDetailViewModel(postId: postId, repository: container.makeCommunityRepository()))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        .background(Color.Background.default)
        .navigationBarHidden(true)
        .safeAreaInset(edge: .bottom) { commentComposer }
        .task {
            currentMemberId = CachedMemberProfile.fetchCached(in: modelContext)?.id
            await viewModel.load()
        }
        .onChange(of: viewModel.didDeletePost) { _, deleted in
            if deleted { dismiss() }
        }
    }

    private var isPostAuthor: Bool {
        currentMemberId != nil && viewModel.detail?.author.memberId == currentMemberId
    }

    private var shouldShowCommentsSection: Bool {
        (viewModel.detail?.commentCount ?? viewModel.comments.count) > 0 || !viewModel.comments.isEmpty
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 0) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.backward")
                    .font(.system(size: 26, weight: .regular))
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 48, height: 48)
            }
            Spacer()

            if isPostAuthor {
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
                        .font(.system(size: 24))
                        .foregroundStyle(Color.Icon.default)
                        .frame(width: 48, height: 48)
                }
            } else {
                Color.clear.frame(width: 48, height: 48)
            }
        }
        .frame(height: 60)
        .padding(.horizontal, 12)
        .background(Color.Background.default)
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        if viewModel.isLoading && viewModel.detail == nil {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let detail = viewModel.detail {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    postBody(detail)
                    if shouldShowCommentsSection {
                        Divider()
                            .padding(.top, Spacing.xl)
                        commentsSection
                            .padding(.top, Spacing.lg)
                    }
                }
                .padding(.horizontal, horizontalInset)
                .padding(.top, Spacing.md)
                .padding(.bottom, Spacing.xl)
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
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: Spacing.sm) {
                postAuthorLine(detail)
                Spacer(minLength: Spacing.md)
                likeButton(detail)
            }
            .frame(height: 32)

            VStack(alignment: .leading, spacing: 0) {
                Text(detail.title)
                    .appTypography(.titleLarge)
                    .foregroundStyle(Color.Text.default)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, horizontalInset)

                if !detail.imageUrls.isEmpty {
                    imageCarousel(detail.imageUrls)
                        .padding(.top, Spacing.md)
                }

                Text(detail.body)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(Color.Text.subtle)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Spacing.md)

                detailTagRow(detail)
                    .padding(.top, horizontalInset)
            }
        }
    }

    private func postAuthorLine(_ detail: CommunityPostDetail) -> some View {
        HStack(spacing: Spacing.sm) {
            CommunityAvatar(profileImageUrl: detail.author.profileImageUrl, size: .small)
            Text(detail.author.nickname ?? "익명")
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.default)
                .lineLimit(1)
            Text("·")
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.muted)
            Text(detailDateText(detail.createdAt))
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.muted)
                .lineLimit(1)
        }
    }

    private func likeButton(_ detail: CommunityPostDetail) -> some View {
        Button {
            Task { await viewModel.toggleLike() }
        } label: {
            Image(systemName: detail.likedByMe ? "heart.fill" : "heart")
                .font(.system(size: 28, weight: .regular))
                .foregroundStyle(detail.likedByMe ? Color.Icon.red : Color.Icon.default)
                .frame(width: 32, height: 32)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(detail.likedByMe ? "좋아요 취소" : "좋아요")
    }

    private func imageCarousel(_ imageUrls: [String]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(imageUrls, id: \.self) { url in
                    CommunityRemoteImage(url: url, cornerRadius: 12)
                        .frame(width: 280, height: 210)
                }
            }
        }
    }

    private func detailTagRow(_ detail: CommunityPostDetail) -> some View {
        HStack(spacing: Spacing.sm) {
            if detail.postType == .question {
                AppBadge(label: "Q&A", size: .medium, style: .solidPastel, variant: .secondary)
            }
            AppBadge(label: detail.cropName, size: .medium, style: .solidPastel, variant: .secondary)
        }
    }

    private func detailDateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM.dd"
        return formatter.string(from: date)
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
                    CommentRow(
                        comment: comment,
                        replyTarget: comment,
                        currentMemberId: currentMemberId,
                        onReply: { target in
                            viewModel.startReply(to: target)
                            commentFieldFocused = true
                        },
                        onDelete: { target in
                            Task { await viewModel.deleteComment(target) }
                        }
                    )
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
            HStack(spacing: 0) {
                Button {} label: {
                    Image(systemName: "photo")
                        .font(.system(size: 24))
                        .foregroundStyle(Color.Icon.default)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                .disabled(true)

                TextField("댓글을 입력해주세요.", text: $viewModel.draftComment, axis: .vertical)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(Color.Text.default)
                    .focused($commentFieldFocused)
                    .lineLimit(1...4)

                Spacer(minLength: 12)

                Button {
                    commentFieldFocused = false
                    Task { await viewModel.submitComment() }
                } label: {
                    if viewModel.isSubmittingComment {
                        ProgressView()
                            .frame(width: 48, height: 48)
                    } else {
                        Image(systemName: "arrow.right")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(canSubmitComment ? Color.Icon.inverse : Color.Icon.disabled)
                            .frame(width: 48, height: 48)
                            .background(Circle().fill(canSubmitComment ? Color.Object.primary : Color.Object.disabled))
                    }
                }
                .disabled(!canSubmitComment)
            }
            .frame(minHeight: 64)
            .padding(.horizontal, 12)
        }
        .background(Color.Background.default)
        .overlay(
            Rectangle()
                .stroke(Color.Border.default, lineWidth: 1)
        )
    }

    private var canSubmitComment: Bool {
        !viewModel.draftComment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !viewModel.isSubmittingComment
    }
}

/// One comment and its replies. Replies always attach to the thread's root comment (`replyTarget`), so the
/// tree stays a single indent level. A soft-deleted comment shows placeholder text but still renders replies.
private struct CommentRow: View {
    let comment: CommunityComment
    /// The root comment of this thread — every "댓글 달기" in the subtree replies to it, never to a reply.
    let replyTarget: CommunityComment
    let currentMemberId: UUID?
    let onReply: (CommunityComment) -> Void
    let onDelete: (CommunityComment) -> Void

    private var isMine: Bool {
        currentMemberId != nil && comment.author.memberId == currentMemberId
    }

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
                        HStack(spacing: Spacing.md) {
                            Button("댓글 달기") { onReply(replyTarget) }
                                .appTypography(.labelMedium)
                                .foregroundStyle(Color.Text.muted)
                            if isMine {
                                Button("삭제") { onDelete(comment) }
                                    .appTypography(.labelMedium)
                                    .foregroundStyle(Color.Text.red)
                            }
                        }
                    }
                }
                Spacer(minLength: 0)
            }

            ForEach(comment.replies) { reply in
                CommentRow(
                    comment: reply,
                    replyTarget: replyTarget,
                    currentMemberId: currentMemberId,
                    onReply: onReply,
                    onDelete: onDelete
                )
                .padding(.leading, Spacing.xl)
            }
        }
    }
}
