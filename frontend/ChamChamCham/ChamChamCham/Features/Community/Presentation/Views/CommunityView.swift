//
//  CommunityView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Community tab root: 자유 이야기 / Q&A tabs, crop board chips, sort control, and a cursor-paged post list.
/// The "+" chip opens the crop picker; the floating pencil opens the composer.
struct CommunityView: View {
    private let container: DIContainer
    @State private var viewModel: CommunityFeedViewModel
    @State private var showCompose = false
    @State private var showCropPicker = false
    @State private var showSearch = false
    @Binding private var selection: Int
    private let tabItems: [AppNavBar.Item]
    private let horizontalInset: CGFloat = 20

    init(container: DIContainer, selection: Binding<Int>, tabItems: [AppNavBar.Item]) {
        self.container = container
        _selection = selection
        self.tabItems = tabItems
        _viewModel = State(
            initialValue: CommunityFeedViewModel(
                repository: container.makeCommunityRepository(),
                cropCatalog: container.makeCropCatalogService()
            )
        )
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                VStack(spacing: 0) {
                    AppTopAppBar(
                        title: "정보 공유",
                        showBorder: false,
                        trailing: [.init(.asset("search")) { showSearch = true }]
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
                CommunityDetailView(postId: post.id, container: container)
            }
        }
        .task { await viewModel.onAppear() }
        .fullScreenCover(isPresented: $showCompose) {
            CommunityComposeView(container: container) { _ in
                Task { await viewModel.reload() }
            }
        }
        .fullScreenCover(isPresented: $showCropPicker) {
            CropPickerView(
                loadCrops: viewModel.catalogCrops,
                loadCategories: viewModel.catalogCategories
            ) { crops in
                Task { await viewModel.addBoards(from: crops) }
            }
        }
        .fullScreenCover(isPresented: $showSearch) {
            SearchView(container: container)
        }
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
                showCropPicker = true
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
                emptyState(text: "아직 게시글이 없어요.", systemImage: "square.stack.3d.up.slash")
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

    private func emptyState(text: String, systemImage: String) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.disabled)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
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
            showCompose = true
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
