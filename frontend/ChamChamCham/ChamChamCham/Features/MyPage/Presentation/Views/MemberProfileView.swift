//
//  MemberProfileView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Navigation value for pushing another member's profile from within an existing `NavigationStack`
/// (e.g. from `CommunityDetailView`'s author line).
struct MemberProfileRoute: Hashable {
    let memberId: UUID
}

/// Read-only profile screen for another member: the same header card + board filter + post list
/// composition as `ProfileMainView`, minus the content tabs (다른 회원의 좋아요 목록은 비공개), the
/// settings/notification actions, and the avatar edit affordance.
struct MemberProfileView: View {
    let memberId: UUID
    let container: DIContainer
    @State private var viewModel: MemberProfileViewModel
    @State private var isShowingBoardSheet = false
    @State private var showBlockSubmittedAlert = false
    @Environment(\.dismiss) private var dismiss

    init(memberId: UUID, container: DIContainer) {
        self.memberId = memberId
        self.container = container
        _viewModel = State(
            initialValue: MemberProfileViewModel(
                memberId: memberId,
                profileRepository: container.makeMemberProfileRepository(),
                communityRepository: container.makeCommunityRepository()
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "프로필",
                isDetail: true,
                leading: .init(.asset("chevron_backward")) { dismiss() },
                trailing: [.init(.asset("more_vert")) { showBlockSubmittedAlert = true }]
            )

            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: []) {
                    profileCard
                        .padding(.horizontal, Spacing.lg - Spacing.xs)
                        .padding(.top, Spacing.md)
                        .padding(.bottom, Spacing.md)

                    filterRow

                    postsSection
                }
            }
        }
        .background(Color.Background.default)
        .navigationBarHidden(true)
        .task { await viewModel.onAppear() }
        .alert("차단 접수 완료", isPresented: $showBlockSubmittedAlert) {
            Button("확인", role: .cancel) {}
        } message: {
            Text("차단이 잘 접수되었습니다. 감사합니다.")
        }
        .sheet(isPresented: $isShowingBoardSheet) {
            BoardSelectSheet(
                activeBoards: viewModel.activeBoards,
                otherBoards: viewModel.otherBoards,
                isLoading: viewModel.isLoadingBoards,
                initialSelection: viewModel.selectedBoardCropIds
            ) { cropIds in
                Task { await viewModel.applyBoardFilter(cropIds: cropIds) }
            }
        }
    }

    // MARK: - Profile card

    private var profileCard: some View {
        VStack(spacing: Spacing.md) {
            avatar

            VStack(spacing: Spacing.sm) {
                Text(viewModel.profile?.nickname ?? "닉네임")
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)

                identityLine
            }

            if !cropNames.isEmpty || viewModel.hiddenCropCount > 0 {
                Divider().overlay(Color.Border.default)
                cropBadges
            }
        }
        .padding(Spacing.lg - Spacing.xs)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20).fill(Color.Object.default)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20).stroke(Color.Border.default, lineWidth: 1)
        )
    }

    @ViewBuilder private var avatar: some View {
        if let urlString = viewModel.profile?.profileImageUrl, let url = URL(string: urlString) {
            AppAvatar(size: .large) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ProgressView()
                }
            }
        } else {
            AppAvatar(size: .large)
        }
    }

    @ViewBuilder private var identityLine: some View {
        let parts = [viewModel.regionText, viewModel.experienceText].compactMap { $0 }
        if !parts.isEmpty {
            HStack(spacing: Spacing.sm) {
                ForEach(Array(parts.enumerated()), id: \.offset) { index, part in
                    if index > 0 {
                        Rectangle()
                            .fill(Color.Border.default)
                            .frame(width: 1, height: 12)
                    }
                    Text(part)
                        .appTypography(.bodyLarge)
                        .foregroundStyle(Color.Text.subtle)
                }
            }
            .lineLimit(1)
        }
    }

    private var cropNames: [String] { viewModel.displayedCropNames }

    @ViewBuilder private var cropBadges: some View {
        let rows = chunk(cropNames, size: 5)
        VStack(spacing: Spacing.sm) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: Spacing.sm) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, name in
                        AppBadge(label: name, style: .solid, variant: .primary)
                    }
                }
            }
            if viewModel.hiddenCropCount > 0 {
                Button {
                    viewModel.cropsExpanded = true
                } label: {
                    Text("외 \(viewModel.hiddenCropCount)종")
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.subtle)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Filter

    private var filterRow: some View {
        HStack {
            AppChip(
                label: viewModel.selectedBoardName ?? "게시판 선택",
                isSelected: !viewModel.selectedBoardCropIds.isEmpty,
                trailingSystemImage: "chevron.down"
            ) {
                isShowingBoardSheet = true
                Task { await viewModel.loadBoardsIfNeeded() }
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.lg - Spacing.xs)
        .padding(.vertical, 14)
        .background(Color.Background.subtle)
    }

    // MARK: - Posts

    @ViewBuilder private var postsSection: some View {
        if viewModel.isLoadingPosts && viewModel.posts.isEmpty {
            LoadingView()
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.xl)
        } else if let error = viewModel.postsErrorMessage, viewModel.posts.isEmpty {
            errorState(error)
        } else if viewModel.posts.isEmpty {
            EmptyStateView(message: "작성한 게시물이 없어요.")
                .padding(.top, Spacing.xl)
        } else {
            LazyVStack(spacing: CommunityPostRow.Layout.interRowSpacing) {
                ForEach(viewModel.posts) { post in
                    NavigationLink(value: post) {
                        CommunityPostRow(post: post) {
                            Task { await viewModel.toggleLike(post) }
                        }
                    }
                    .buttonStyle(.plain)
                    .task { await viewModel.loadMoreIfNeeded(currentItem: post) }
                }
                if viewModel.isLoadingMore {
                    ProgressView().padding(.vertical, Spacing.md)
                }
            }
            .padding(.top, Spacing.md)
        }
    }

    private func errorState(_ message: String) -> some View {
        VStack(spacing: Spacing.md) {
            Text(message)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .multilineTextAlignment(.center)
            Button("다시 시도") {
                Task { await viewModel.reloadPosts() }
            }
            .appTypography(.bodyMediumEmphasized)
            .foregroundStyle(Color.Text.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl)
        .padding(.horizontal, Spacing.lg)
    }

    private func chunk(_ items: [String], size: Int) -> [[String]] {
        guard size > 0 else { return [items] }
        return stride(from: 0, to: items.count, by: size).map {
            Array(items[$0..<min($0 + size, items.count)])
        }
    }
}
