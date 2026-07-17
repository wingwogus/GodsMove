//
//  CommunityView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftData
import SwiftUI

/// Community tab root: 자유 이야기 / Q&A tabs, crop board chips, sort control, and a cursor-paged post list.
/// The "+" chip opens the crop picker; the floating pencil opens the composer.
struct CommunityView: View {
    private let container: DIContainer
    /// Browsing without an account. Passed down from `MainTabView`, not read from `AppState` in `init` —
    /// `@Environment` isn't populated yet at init time.
    private let isGuest: Bool
    @Environment(AppState.self) private var appState
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: CommunityFeedViewModel
    @State private var showCompose = false
    @State private var showCropPicker = false
    @State private var showSearch = false
    @State private var showLoginRequiredAlert = false
    @Binding private var selection: Int
    private let tabItems: [AppNavBar.Item]
    private let horizontalInset: CGFloat = 20

    init(container: DIContainer, selection: Binding<Int>, tabItems: [AppNavBar.Item], isGuest: Bool = false) {
        self.container = container
        self.isGuest = isGuest
        _selection = selection
        self.tabItems = tabItems
        _viewModel = State(
            initialValue: CommunityFeedViewModel(
                repository: container.makeCommunityRepository(),
                cropCatalog: container.makeCropCatalogService(),
                extraBoardStore: container.extraCropBoardStore,
                isGuest: isGuest
            )
        )
    }

    /// Runs `action` if signed in; a guest gets a login prompt instead. Guards every write/personalized
    /// action (게시글 작성, 좋아요, 작물 보드 추가) so a token-less request never even fires.
    private func requireAuth(_ action: () -> Void) {
        guard !isGuest else {
            showLoginRequiredAlert = true
            return
        }
        action()
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                VStack(spacing: 0) {
                    AppTopAppBar(
                        title: "정보 공유",
                        showBorder: false,
                        trailing: [.init(.asset("search")) { requireAuth { showSearch = true } }]
                    )
                    postTypeTabs
                    cropChipRow
                    postList
                }
                writeButton
            }
            .appTabBarDock(items: tabItems, selection: $selection)
            .navigationBarHidden(true)
            .navigationDestination(for: CommunityPostSummary.self) { post in
                CommunityDetailView(postId: post.id, container: container, isGuest: isGuest)
            }
        }
        .task {
            let memberId = isGuest ? nil : CachedMemberProfile.fetchCached(in: modelContext)?.id
            await viewModel.onAppear(memberId: memberId)
        }
        .fullScreenCover(isPresented: $showCompose) {
            CommunityComposeView(container: container) { _ in
                Task { await viewModel.reload() }
            }
        }
        .fullScreenCover(isPresented: $showCropPicker) {
            CropPickerView(
                loadCrops: viewModel.catalogCrops,
                loadCategories: viewModel.catalogCategories,
                initialSelectedCropIDs: viewModel.boards.map(\.cropId)
            ) { crops in
                Task { await viewModel.addBoards(from: crops) }
            }
        }
        .fullScreenCover(isPresented: $showSearch) {
            SearchView(container: container)
        }
        .loginRequiredAlert(isPresented: $showLoginRequiredAlert, appState: appState)
    }

    // MARK: - Post type tabs (자유 이야기 / Q&A)

    private var postTypeTabs: some View {
        AppTabBar(
            titles: ["일반 게시물", "Q&A 게시물"],
            selection: Binding(
                get: { viewModel.postType == .general ? 0 : 1 },
                set: { index in
                    Task {
                        await viewModel.selectPostType(index == 0 ? .general : .question)
                    }
                }
            )
        )
        .frame(height: 56)
    }

    // MARK: - Crop board chips

    private var cropChipRow: some View {
        HStack(spacing: 0) {
            Button {
                requireAuth { showCropPicker = true }
            } label: {
                AppIconView(source: .asset("add"), size: 24)
                    .foregroundStyle(Color.Icon.subtle)
                    .frame(width: 32, height: 32)
                    .background(Color.Object.default)
                    .overlay(Circle().stroke(Color.Border.default, lineWidth: 1))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .frame(width: 60, height: 60)
            .overlay(alignment: .trailing) {
                Rectangle()
                    .fill(Color.Border.default)
                    .frame(width: 1, height: 24)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    let isAllSelected = viewModel.selectedCropId == nil
                    AppChip(
                        label: "전체",
                        isSelected: isAllSelected,
                        style: isAllSelected ? .solid : .solidPastel
                    ) {
                        Task { await viewModel.selectCrop(nil) }
                    }
                    ForEach(viewModel.boards) { board in
                        let isSelected = viewModel.selectedCropId == board.cropId
                        AppChip(
                            label: board.cropName,
                            isSelected: isSelected,
                            style: isSelected ? .solid : .solidPastel
                        ) {
                            Task { await viewModel.selectCrop(board.cropId) }
                        }
                    }
                }
                .padding(.leading, Spacing.sm)
                .padding(.trailing, horizontalInset)
                .padding(.vertical, 14)
            }
        }
        .frame(height: 60)
        .background(Color.Background.subtle)
    }

    // MARK: - List

    private var postList: some View {
        ScrollView {
            sortRow
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.top, Spacing.xl)
            } else if let errorMessage = viewModel.errorMessage {
                emptyState(text: errorMessage, systemImage: "exclamationmark.triangle")
            } else if viewModel.posts.isEmpty {
                communityEmptyState
            } else {
                LazyVStack(spacing: CommunityPostRow.Layout.interRowSpacing) {
                    ForEach(viewModel.posts) { post in
                        NavigationLink(value: post) {
                            CommunityPostRow(post: post) {
                                requireAuth { Task { await viewModel.toggleLike(post) } }
                            }
                        }
                        .buttonStyle(.plain)
                        .task { await viewModel.loadMoreIfNeeded(currentItem: post) }
                    }
                    if viewModel.isLoadingMore {
                        ProgressView().padding(Spacing.md)
                    }
                }
                .padding(.bottom, 112)
            }
        }
        .refreshable { await viewModel.reload() }
    }

    private var sortSelection: Binding<CommunityPostSort> {
        Binding(
            get: { viewModel.sort },
            set: { newValue in Task { await viewModel.selectSort(newValue) } }
        )
    }

    private var sortRow: some View {
        HStack {
            Spacer()
            Picker(selection: sortSelection) {
                Text("최신순").tag(CommunityPostSort.latest)
                Text("인기순").tag(CommunityPostSort.popular)
            } label: {
                AppSortButton(title: viewModel.sort == .popular ? "인기순" : "최신순")
            }
            .pickerStyle(.menu)
            .tint(Color.Text.subtle)
        }
        .frame(height: 48)
        .padding(.horizontal, horizontalInset)
        .background(Color.Background.default)
    }

    /// A crop-board chip filtered down to nothing is easy to mistake for "community is empty" — name
    /// the selected board and offer a one-tap way back to "전체" rather than a bare "no posts" message.
    @ViewBuilder private var communityEmptyState: some View {
        if let cropId = viewModel.selectedCropId,
           let cropName = viewModel.boards.first(where: { $0.cropId == cropId })?.cropName {
            VStack(spacing: Spacing.md) {
                Image(systemName: "square.stack.3d.up.slash")
                    .font(.system(size: 40))
                    .foregroundStyle(Color.Icon.disabled)
                Text("'\(cropName)' 게시판에는 아직 게시글이 없어요.\n다른 작물 게시판이나 '전체'를 확인해보세요.")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .multilineTextAlignment(.center)
                Button("전체 게시글 보기") {
                    Task { await viewModel.selectCrop(nil) }
                }
                .appTypography(.bodyMediumEmphasized)
                .foregroundStyle(Color.Text.primary)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.xl * 2)
        } else {
            emptyState(text: "아직 게시글이 없어요.\n첫 이야기를 남겨보세요!", systemImage: "square.stack.3d.up.slash")
        }
    }

    private func emptyState(text: String, systemImage: String) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.disabled)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
    }

    private var writeButton: some View {
        AppButton(
            icon: .asset("add_2"),
            variant: .primary,
            size: .xlarge
        ) {
            requireAuth { showCompose = true }
        }
        .padding(.trailing, horizontalInset)
        .padding(.bottom, Spacing.xl)
        .accessibilityLabel("게시물 작성")
    }
}

/// One row in the community list: badges/date header, title, one-line preview, reactions, and a
/// fixed thumbnail slot.
struct CommunityPostRow: View {
    enum Layout {
        static let interRowSpacing: CGFloat = 20
    }

    let post: CommunityPostSummary
    var onTapLike: () -> Void
    var showsDivider: Bool = true

    var body: some View {
        AppListItem(
            size: .medium,
            title: post.title,
            caption: post.bodyPreview,
            badges: badges,
            dateText: rowDateText(post.createdAt),
            likeText: "\(post.likeCount)",
            commentText: "\(post.commentCount)",
            showsDivider: showsDivider
        ) {
            CommunityRemoteImage(url: post.thumbnailUrl)
        }
        .overlay(alignment: .topLeading) {
            Button(action: onTapLike) {
                Color.clear
                    .frame(width: 48, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .offset(x: 18, y: 106)
            .accessibilityLabel(post.likedByMe ? "좋아요 취소" : "좋아요")
        }
    }

    private var badges: [AppListItemBadge] {
        let category = AppListItemBadge(post.cropName, style: .solidPastel, variant: .primary)
        return post.postType == .question ? [category, AppListItemBadge("Q&A")] : [category]
    }

    private func rowDateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd"
        return formatter.string(from: date)
    }
}
