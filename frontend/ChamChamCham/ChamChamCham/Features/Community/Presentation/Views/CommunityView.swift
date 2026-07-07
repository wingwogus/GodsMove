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

    init(container: DIContainer) {
        self.container = container
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
                        title: "커뮤니티",
                        trailing: [.init("magnifyingglass"), .init("bell")]
                    )
                    postTypeTabs
                    cropChipRow
                    Divider()
                    postList
                }
                writeButton
            }
            .navigationBarHidden(true)
            .navigationDestination(for: CommunityPostSummary.self) { post in
                CommunityDetailView(postId: post.id, container: container)
            }
        }
        .task { await viewModel.onAppear() }
        .sheet(isPresented: $showCompose) {
            CommunityComposeView(container: container) { _ in
                Task { await viewModel.reload() }
            }
        }
        .sheet(isPresented: $showCropPicker) {
            CropPickerSheet(loadCrops: viewModel.catalogCrops) { crops in
                Task { await viewModel.addBoards(from: crops) }
            }
        }
    }

    // MARK: - Post type tabs (자유 이야기 / Q&A)

    private var postTypeTabs: some View {
        HStack(spacing: 0) {
            tab(title: "자유 이야기", type: .general)
            tab(title: "Q&A", type: .question)
        }
        .padding(.top, Spacing.sm)
    }

    private func tab(title: String, type: CommunityPostType) -> some View {
        let isSelected = viewModel.postType == type
        return Button {
            Task { await viewModel.selectPostType(type) }
        } label: {
            VStack(spacing: Spacing.sm) {
                Text(title)
                    .appTypography(isSelected ? .bodyLargeEmphasized : .bodyLarge)
                    .foregroundStyle(isSelected ? Color.Text.default : Color.Text.muted)
                Rectangle()
                    .fill(isSelected ? Color.Object.bold : Color.clear)
                    .frame(height: 2)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Crop board chips

    private var cropChipRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                AppChip(label: "전체", isSelected: viewModel.selectedCropId == nil) {
                    Task { await viewModel.selectCrop(nil) }
                }
                ForEach(viewModel.boards) { board in
                    AppChip(label: board.cropName, isSelected: viewModel.selectedCropId == board.cropId) {
                        Task { await viewModel.selectCrop(board.cropId) }
                    }
                }
                Button {
                    showCropPicker = true
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 16))
                        .foregroundStyle(Color.Icon.subtle)
                        .frame(width: 32, height: 32)
                        .background(Circle().stroke(Color.Border.default, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
        }
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
                LazyVStack(spacing: 0) {
                    ForEach(viewModel.posts) { post in
                        NavigationLink(value: post) {
                            CommunityPostRow(post: post) {
                                Task { await viewModel.toggleLike(post) }
                            }
                        }
                        .buttonStyle(.plain)
                        .task { await viewModel.loadMoreIfNeeded(currentItem: post) }
                        Divider().padding(.horizontal, Spacing.md)
                    }
                    if viewModel.isLoadingMore {
                        ProgressView().padding(Spacing.md)
                    }
                }
            }
        }
        .refreshable { await viewModel.reload() }
    }

    private var sortRow: some View {
        HStack {
            Spacer()
            Menu {
                Button("최신순") { Task { await viewModel.selectSort(.latest) } }
                Button("인기순") { Task { await viewModel.selectSort(.popular) } }
            } label: {
                HStack(spacing: Spacing.xs) {
                    Text(viewModel.sort == .popular ? "인기순" : "최신순")
                    Image(systemName: "chevron.down")
                }
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.subtle)
            }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
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
        Button {
            showCompose = true
        } label: {
            Image(systemName: "pencil")
                .font(.system(size: 24))
                .foregroundStyle(Color.Icon.inverse)
                .frame(width: 56, height: 56)
                .background(Color.Object.primary)
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
        }
        .padding(Spacing.lg)
    }
}

/// One row in the community list: title, one-line preview, [Q&A]/[crop] tags, author line, metrics, and an
/// optional thumbnail. Matches the "게시물 리스트 필수 제시 항목" in the wireframe.
struct CommunityPostRow: View {
    let post: CommunityPostSummary
    var onTapLike: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(post.title)
                    .appTypography(.bodyLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .lineLimit(1)
                    .multilineTextAlignment(.leading)

                if !post.bodyPreview.isEmpty {
                    Text(post.bodyPreview)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.subtle)
                        .lineLimit(1)
                        .multilineTextAlignment(.leading)
                }

                CommunityTagRow(postType: post.postType, cropName: post.cropName)

                HStack {
                    CommunityAuthorLine(author: post.author, createdAt: post.createdAt)
                    Spacer()
                    CommunityMetrics(
                        likeCount: post.likeCount,
                        likedByMe: post.likedByMe,
                        commentCount: post.commentCount,
                        onTapLike: onTapLike
                    )
                }
            }

            if let thumbnailUrl = post.thumbnailUrl {
                CommunityRemoteImage(url: thumbnailUrl)
                    .frame(width: 80, height: 80)
            }
        }
        .padding(.vertical, Spacing.md)
        .padding(.horizontal, Spacing.md)
        .contentShape(Rectangle())
    }
}
