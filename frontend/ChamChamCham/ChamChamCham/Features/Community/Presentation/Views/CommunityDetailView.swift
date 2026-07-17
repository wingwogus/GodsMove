//
//  CommunityDetailView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import PhotosUI
import SwiftData
import SwiftUI

/// Post detail with the comment tree and a bottom composer. Pushed from the feed's `navigationDestination`.
struct CommunityDetailView: View {
    let postId: UUID
    private let container: DIContainer
    /// Browsing without an account. Passed down from the caller, not read from `AppState` in `init` —
    /// `@Environment` isn't populated yet at init time.
    private let isGuest: Bool
    private let horizontalInset: CGFloat = 20

    @Environment(AppState.self) private var appState
    @State private var viewModel: CommunityDetailViewModel
    /// The logged-in member, read from the local cache — used to show delete only on the user's own content.
    @State private var currentMemberId: UUID?
    @State private var showLoginRequiredAlert = false
    @FocusState private var commentFieldFocused: Bool
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showPhotoPicker = false
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    init(postId: UUID, container: DIContainer, isGuest: Bool = false) {
        self.postId = postId
        self.container = container
        self.isGuest = isGuest
        _viewModel = State(initialValue: CommunityDetailViewModel(
            postId: postId,
            repository: container.makeCommunityRepository(),
            mediaRepository: container.makeMediaUploadRepository(),
            recordRepository: container.makeRecordRepository()
        ))
    }

    /// Runs `action` if signed in; a guest gets a login prompt instead. Guards every write/personalized
    /// action (좋아요, 댓글 작성/사진 첨부, 다른 회원 프로필 이동) so a token-less request never even fires.
    private func requireAuth(_ action: () -> Void) {
        guard !isGuest else {
            showLoginRequiredAlert = true
            return
        }
        action()
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        .background(Color.Background.default)
        .navigationBarHidden(true)
        .navigationDestination(for: MemberProfileRoute.self) { route in
            MemberProfileView(memberId: route.memberId, container: container)
        }
        .safeAreaInset(edge: .bottom) { commentComposer }
        .photosPicker(isPresented: $showPhotoPicker, selection: $pickerItems, maxSelectionCount: 1, matching: .images)
        .onChange(of: pickerItems) { _, items in
            Task { await attach(items) }
        }
        .task {
            currentMemberId = CachedMemberProfile.fetchCached(in: modelContext)?.id
            await viewModel.load()
        }
        .onChange(of: viewModel.didDeletePost) { _, deleted in
            if deleted { dismiss() }
        }
        .loginRequiredAlert(isPresented: $showLoginRequiredAlert, appState: appState)
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
                AppIconView(source: .asset("chevron_backward"), size: 32)
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
                    AppIconView(source: .asset("more_vert"), size: 24)
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
                            .padding(.horizontal, -horizontalInset)
                    }
                }
                .padding(.horizontal, horizontalInset)
                .padding(.top, Spacing.md)
                .padding(.bottom, Spacing.xl)
            }
        } else {
            VStack(spacing: Spacing.md) {
                AppIconView(source: .asset("error"), size: 40)
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
            detailTagRow(detail)

            HStack(alignment: .center, spacing: Spacing.sm) {
                authorLine(detail)
                Spacer(minLength: Spacing.md)
            }
            .frame(height: 48)
            .padding(.top, Spacing.md)

            VStack(alignment: .leading, spacing: 0) {
                Text(detail.title)
                    .appTypography(.titleLarge)
                    .foregroundStyle(Color.Text.default)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Spacing.md)

                if !detail.imageUrls.isEmpty {
                    imageCarousel(detail.imageUrls)
                        .padding(.top, Spacing.md)
                }

                Text(detail.body)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(Color.Text.subtle)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Spacing.sm)

                if let farmingRecord = viewModel.farmingRecord {
                    CommunityFarmingRecordCard(record: farmingRecord)
                        .padding(.top, Spacing.md)
                }

                reactionRow(detail)
                    .padding(.top, Spacing.md)
            }
        }
    }

    /// `♥ likeCount   💬 commentCount` under the post body. Tapping 💬 focuses the composer.
    private func reactionRow(_ detail: CommunityPostDetail) -> some View {
        HStack(spacing: Spacing.md) {
            likeButton(detail)
            Button {
                requireAuth { commentFieldFocused = true }
            } label: {
                HStack(spacing: Spacing.xs) {
                    AppIconView(source: .asset("chat_bubble_line"), size: 24)
                        .foregroundStyle(Color.Icon.default)
                    Text("\(detail.commentCount)")
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.subtle)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("댓글 달기")
        }
    }

    @ViewBuilder
    private func authorLine(_ detail: CommunityPostDetail) -> some View {
        if isPostAuthor {
            postAuthorLine(detail)
        } else if isGuest {
            Button { showLoginRequiredAlert = true } label: {
                postAuthorLine(detail)
            }
            .buttonStyle(.plain)
        } else {
            NavigationLink(value: MemberProfileRoute(memberId: detail.author.memberId)) {
                postAuthorLine(detail)
            }
            .buttonStyle(.plain)
        }
    }

    private func postAuthorLine(_ detail: CommunityPostDetail) -> some View {
        HStack(spacing: Spacing.sm) {
            CommunityAvatar(profileImageUrl: detail.author.profileImageUrl, size: .medium)
            VStack(alignment: .leading, spacing: 0) {
                Text(detail.author.nickname ?? "익명")
                    .appTypography(.bodyMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
                Text(detailDateText(detail.createdAt))
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .lineLimit(1)
            }
        }
    }

    private func likeButton(_ detail: CommunityPostDetail) -> some View {
        Button {
            requireAuth { Task { await viewModel.toggleLike() } }
        } label: {
            HStack(spacing: Spacing.xs) {
                AppIconView(source: .asset(detail.likedByMe ? "favorite" : "favorite_line"), size: 24)
                    .foregroundStyle(detail.likedByMe ? Color.Icon.red : Color.Icon.default)
                Text("\(detail.likeCount)")
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.subtle)
            }
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
                AppBadge(label: "Q&A", size: .medium, style: .solid, variant: .primary)
            }
            AppBadge(label: detail.cropName, size: .medium, style: .solidPastel, variant: .primary)
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
                .padding(.horizontal, horizontalInset)

            if viewModel.comments.isEmpty {
                Text("첫 댓글을 남겨보세요.")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .padding(.vertical, Spacing.md)
                    .padding(.horizontal, horizontalInset)
            } else {
                ForEach(viewModel.comments) { comment in
                    CommentRow(
                        comment: comment,
                        replyTarget: comment,
                        currentMemberId: currentMemberId,
                        onReply: { target in
                            requireAuth {
                                viewModel.startReply(to: target)
                                commentFieldFocused = true
                            }
                        },
                        onDelete: { target in
                            Task { await viewModel.deleteComment(target) }
                        }
                    )
                    .task { await viewModel.loadMoreCommentsIfNeeded(currentItem: comment) }
                }
                if viewModel.isLoadingMoreComments {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, horizontalInset)
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
            if let attachment = viewModel.attachment {
                AppCommentInput(
                    text: $viewModel.draftComment,
                    isFocused: $commentFieldFocused,
                    isSubmitting: viewModel.isSubmittingComment,
                    isPhotoEnabled: true,
                    onPhotoTap: { requireAuth { showPhotoPicker = true } },
                    onRemoveAttachment: { viewModel.removeImage() },
                    onSubmit: {
                        requireAuth {
                            commentFieldFocused = false
                            Task { await viewModel.submitComment() }
                        }
                    },
                    attachment: { attachmentThumbnail(attachment) }
                )
            } else {
                AppCommentInput(
                    text: $viewModel.draftComment,
                    isFocused: $commentFieldFocused,
                    isSubmitting: viewModel.isSubmittingComment,
                    isPhotoEnabled: true,
                    onPhotoTap: { requireAuth { showPhotoPicker = true } },
                    onSubmit: {
                        requireAuth {
                            commentFieldFocused = false
                            Task { await viewModel.submitComment() }
                        }
                    }
                )
            }
        }
        .background(Color.Background.default)
        .ignoresSafeArea(edges: .bottom)
    }

    /// The picked image is shown immediately; while its upload is in flight a dimmed spinner overlays it.
    @ViewBuilder
    private func attachmentThumbnail(_ attachment: CommunityDetailViewModel.Attachment) -> some View {
        ZStack {
            if let uiImage = UIImage(data: attachment.previewData) {
                Image(uiImage: uiImage).resizable().scaledToFill()
            } else {
                Color.Object.muted
            }
            if attachment.isUploading {
                Color.black.opacity(0.3)
                ProgressView()
                    .tint(Color.Icon.inverse)
            }
        }
    }

    private func attach(_ items: [PhotosPickerItem]) async {
        defer { pickerItems = [] }
        guard let item = items.first,
              let data = try? await item.loadTransferable(type: Data.self) else { return }
        await viewModel.addImage(data)
    }
}

/// The 영농일지 card shown in post detail when the post shares a record (BR-COMMUNITY-002, Figma `커뮤니티 /
/// 게시물 내 영농일지 포함 시`). Unlike `AppCard`, this composition has no thumbnail — a farming record's detail
/// response carries no title, so 활동 유형 (`workType.label`) stands in for it.
private struct CommunityFarmingRecordCard: View {
    private static let padding: CGFloat = 20

    let record: RecordDetail

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                AppBadge(label: record.cropName, size: .medium, style: .solidPastel, variant: .secondary)
                Spacer()
                Text(dateText)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
            }

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(record.workType.label)
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                Text(record.memo)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(Color.Text.muted)
                    .lineLimit(2)
            }
        }
        .padding(Self.padding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.default)
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.Border.default, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd"
        return formatter.string(from: record.workedAt)
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
    @State private var isReadMoreActive = false

    private var isMine: Bool {
        currentMemberId != nil && comment.author.memberId == currentMemberId
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            commentCell

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

    @ViewBuilder private var commentCell: some View {
        if let imageUrl = comment.imageUrl, !comment.isDeleted {
            AppComment(
                nickname: comment.author.nickname ?? "익명",
                dateText: CommunityRelativeTime.string(from: comment.createdAt),
                bodyText: comment.body,
                isReadMoreActive: isReadMoreActive,
                isMyComment: isMine,
                onReadMore: { isReadMoreActive.toggle() },
                onReply: { onReply(replyTarget) },
                onDelete: { onDelete(comment) },
                avatar: {
                    CommunityAvatar(profileImageUrl: comment.author.profileImageUrl, size: .small)
                },
                attachment: {
                    CommunityRemoteImage(url: imageUrl, cornerRadius: 8)
                }
            )
        } else {
            AppComment(
                nickname: comment.author.nickname ?? "익명",
                dateText: CommunityRelativeTime.string(from: comment.createdAt),
                bodyText: comment.isDeleted ? "삭제된 댓글입니다." : comment.body,
                isReadMoreActive: isReadMoreActive,
                isMyComment: isMine,
                bodyColor: comment.isDeleted ? Color.Text.muted : Color.Text.default,
                showsActions: !comment.isDeleted,
                showsHeaderAction: !comment.isDeleted,
                onReadMore: { isReadMoreActive.toggle() },
                onReply: { onReply(replyTarget) },
                onDelete: { onDelete(comment) },
                avatar: {
                    CommunityAvatar(profileImageUrl: comment.author.profileImageUrl, size: .small)
                }
            )
        }
    }
}
