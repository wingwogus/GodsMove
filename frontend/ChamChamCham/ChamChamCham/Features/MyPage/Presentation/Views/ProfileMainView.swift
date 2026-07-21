
//
//  ProfileMainView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 프로필 메인 (마이페이지 탭 루트). Member header card + 나의 게시물 / 좋아요 누른 글 tabs +
/// board filter chip + paginated post list. The settings icon presents the settings screen; the
/// board filter sheet is wired in a later step.
struct ProfileMainView: View {
    let container: DIContainer
    @State private var viewModel: ProfileMainViewModel
    @State private var isShowingSettings = false
    @State private var isShowingBoardSheet = false
    @State private var isShowingProfileEdit = false
    @State private var profileToast: String?
    @Binding private var selection: Int
    private let tabItems: [AppNavBar.Item]

    init(container: DIContainer, selection: Binding<Int>, tabItems: [AppNavBar.Item]) {
        self.container = container
        _selection = selection
        self.tabItems = tabItems
        _viewModel = State(
            initialValue: ProfileMainViewModel(
                profileRepository: container.makeMemberProfileRepository(),
                communityRepository: container.makeCommunityRepository()
            )
        )
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                AppTopAppBar(
                    title: "나의 프로필",
                    trailing: [
                        .init(.asset("settings")) { isShowingSettings = true }
                    ]
                )

                ScrollView {
                    LazyVStack(spacing: 0, pinnedViews: []) {
                        profileCard
                            .padding(.horizontal, Spacing.lg - Spacing.xs)
                            .padding(.top, Spacing.md)
                            .padding(.bottom, Spacing.md)

                        AppTabBar(
                            titles: ["나의 게시물", "좋아요 누른 글"],
                            selection: $viewModel.selectedTabIndex
                        )

                        filterRow

                        postsSection
                    }
                }
            }
            .background(Color.Background.default)
            .appTabBarDock(items: tabItems, selection: $selection)
            .navigationDestination(for: CommunityPostSummary.self) { post in
                CommunityDetailView(postId: post.id, container: container)
            }
        }
        .appToast(message: $profileToast)
        .task { await viewModel.onAppear() }
        .onChange(of: viewModel.selectedTabIndex) { _, _ in
            Task { await viewModel.onTabChanged() }
        }
        .fullScreenCover(isPresented: $isShowingSettings) {
            SettingsView(authRepository: container.makeAuthRepository())
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
        .fullScreenCover(isPresented: $isShowingProfileEdit, onDismiss: {
            Task { await viewModel.loadProfile() }
        }) {
            ProfileEditView(container: container) {
                profileToast = "기본 정보 수정 완료되었습니다."
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

    private var avatar: some View {
        Button {
            isShowingProfileEdit = true
        } label: {
            avatarContent
                .overlay(alignment: .bottomTrailing) {
                    Circle()
                        .fill(Color.Object.bold)
                        .frame(width: 36, height: 36)
                        .overlay {
                            AppIconView(source: .asset("edit"), size: 16)
                                .foregroundStyle(Color.Icon.inverse)
                        }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("프로필 수정")
    }

    @ViewBuilder private var avatarContent: some View {
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
        AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm, alignment: .center) {
            ForEach(Array(cropNames.enumerated()), id: \.offset) { _, name in
                AppBadge(label: name, style: .solid, variant: .primary)
            }
            if viewModel.canToggleCrops {
                Button {
                    viewModel.cropsExpanded.toggle()
                } label: {
                    Text(viewModel.cropsExpanded ? "접기" : "외 \(viewModel.hiddenCropCount)종")
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
                trailingSystemImage: .asset("keyboard_arrow_down")
            ) {
                isShowingBoardSheet = true
                Task { await viewModel.loadCropFilterOptionsIfNeeded() }
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
            VStack(spacing: Spacing.md) {
                EmptyStateView(message: emptyMessage)
                if !viewModel.selectedBoardCropIds.isEmpty {
                    Button("전체 게시판 보기") {
                        Task { await viewModel.applyBoardFilter(cropIds: []) }
                    }
                    .appTypography(.bodyMediumEmphasized)
                    .foregroundStyle(Color.Text.primary)
                }
            }
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

    private var emptyMessage: String {
        let hasBoardFilter = !viewModel.selectedBoardCropIds.isEmpty
        switch (viewModel.currentTab, hasBoardFilter) {
        case (.myPosts, false): return "작성한 게시물이 없어요."
        case (.myPosts, true): return "선택한 게시판에 작성한 게시물이 없어요."
        case (.likedPosts, false): return "좋아요 누른 글이 없어요."
        case (.likedPosts, true): return "선택한 게시판에 좋아요 누른 글이 없어요."
        }
    }
}
