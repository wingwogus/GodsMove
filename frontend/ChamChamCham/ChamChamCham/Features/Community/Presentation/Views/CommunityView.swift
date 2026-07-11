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
    private let horizontalInset: CGFloat = 20

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
                        showBorder: false,
                        trailing: [.init("magnifyingglass"), .init("bell")]
                    )
                    postTypeTabs
                    cropChipRow
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
        .fullScreenCover(isPresented: $showCompose) {
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
            tab(title: "일반 게시물", type: .general)
            tab(title: "Q&A 게시물", type: .question)
        }
        .frame(height: 56)
        .background(Color.Background.default)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.Border.subtle)
                .frame(height: 1)
        }
    }

    private func tab(title: String, type: CommunityPostType) -> some View {
        let isSelected = viewModel.postType == type
        return Button {
            Task { await viewModel.selectPostType(type) }
        } label: {
            Text(title)
                .appTypography(isSelected ? .titleMediumEmphasized : .titleMedium)
                .foregroundStyle(isSelected ? Color.Text.default : Color.Text.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.86)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .overlay(alignment: .bottom) {
                    if isSelected {
                        Rectangle()
                            .fill(Color.Border.primary)
                            .frame(height: 3)
                    }
                }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Crop board chips

    private var cropChipRow: some View {
        HStack(spacing: 0) {
            Button {
                showCropPicker = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .medium))
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
                    AppChip(label: "전체", isSelected: viewModel.selectedCropId == nil, style: .solid) {
                        Task { await viewModel.selectCrop(nil) }
                    }
                    ForEach(viewModel.boards) { board in
                        AppChip(label: board.cropName, isSelected: viewModel.selectedCropId == board.cropId, style: .solid) {
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
                LazyVStack(spacing: 0) {
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
        Button {
            showCompose = true
        } label: {
            Image(systemName: "pencil")
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.inverse)
                .frame(width: 72, height: 72)
                .background(Color.Object.primary)
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
        }
        .padding(.trailing, horizontalInset)
        .padding(.bottom, Spacing.xl)
        .accessibilityLabel("게시물 작성")
    }
}

/// One row in the community list: badges/date header, title, one-line preview, reactions, and a
/// fixed thumbnail slot.
struct CommunityPostRow: View {
    let post: CommunityPostSummary
    var onTapLike: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(post.title)
                            .appTypography(.titleLargeEmphasized)
                            .foregroundStyle(Color.Text.subtle)
                            .lineLimit(1)
                            .minimumScaleFactor(0.86)
                            .multilineTextAlignment(.leading)

                        if !post.bodyPreview.isEmpty {
                            Text(post.bodyPreview)
                                .appTypography(.bodyLarge)
                                .foregroundStyle(Color.Text.muted)
                                .lineLimit(1)
                                .minimumScaleFactor(0.9)
                                .multilineTextAlignment(.leading)
                        }
                    }

                    Spacer(minLength: 0)

                    reactionRow
                }
                .frame(maxWidth: .infinity, minHeight: 96, maxHeight: 96, alignment: .topLeading)

                CommunityRemoteImage(url: post.thumbnailUrl)
                    .frame(width: 96, height: 96)
            }
        }
        .padding(.horizontal, 20)
        .frame(height: 160, alignment: .top)
        .overlay {
            Rectangle()
                .stroke(Color.Border.default, lineWidth: 1)
        }
        .contentShape(Rectangle())
    }

    private var header: some View {
        HStack(alignment: .center, spacing: Spacing.sm) {
            HStack(spacing: Spacing.sm) {
                AppBadge(label: post.cropName, size: .medium, style: .solidPastel, variant: .secondary)
                if post.postType == .question {
                    AppBadge(label: "Q&A", size: .medium, style: .solidPastel, variant: .secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(rowDateText(post.createdAt))
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
                .lineLimit(1)
        }
        .frame(height: 32)
    }

    private var reactionRow: some View {
        HStack(spacing: 12) {
            Button {
                onTapLike()
            } label: {
                reaction(systemName: post.likedByMe ? "heart.fill" : "heart", text: "\(post.likeCount)")
                    .foregroundStyle(post.likedByMe ? Color.Icon.red : Color.Text.muted)
            }
            .buttonStyle(.plain)

            reaction(systemName: "bubble.left", text: "\(post.commentCount)")
                .foregroundStyle(Color.Text.muted)
        }
    }

    private func reaction(systemName: String, text: String) -> some View {
        HStack(spacing: 2) {
            Image(systemName: systemName)
                .font(.system(size: 22))
                .frame(width: 24, height: 24)
            Text(text)
                .appTypography(.bodyMedium)
        }
    }

    private func rowDateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd"
        return formatter.string(from: date)
    }
}
