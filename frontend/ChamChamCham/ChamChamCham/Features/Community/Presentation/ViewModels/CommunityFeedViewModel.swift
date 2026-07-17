//
//  CommunityFeedViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Drives the community list: the 자유 이야기 / Q&A tab (postType), the crop board chips, the sort control, and
/// cursor pagination. All filter changes re-query from the first page.
@MainActor
@Observable
final class CommunityFeedViewModel {
    /// 자유 이야기(`.general`) / Q&A(`.question`) top tab. One is always active.
    private(set) var postType: CommunityPostType = .general
    /// nil == "전체" (no crop filter).
    private(set) var selectedCropId: UUID?
    /// Only 최신순/인기순 are offered in the UI (the backend also has LIKE/COMMENT, unused here).
    private(set) var sort: CommunityPostSort = .latest

    private(set) var posts: [CommunityPostSummary] = []
    /// Crop boards for the chip row. Server boards (`/boards`) merged with any the user added via the picker
    /// this session — there's no "follow board" endpoint, so extras are session-local.
    private(set) var boards: [CommunityBoard] = []
    private(set) var isLoading = false
    private(set) var isLoadingMore = false
    private(set) var errorMessage: String?

    private var nextCursor: String?
    private var serverBoards: [CommunityBoard] = []
    private var extraBoards: [CommunityBoard] = []

    private let repository: any CommunityRepository
    private let cropCatalog: any CropCatalogService
    /// Browsing without an account — `/boards` still requires auth, and there's no "나의 작물" concept to
    /// restrict "전체" to, so both are skipped entirely for a guest (see `filteredForAllCrops`).
    private let isGuest: Bool

    init(repository: any CommunityRepository, cropCatalog: any CropCatalogService, isGuest: Bool = false) {
        self.repository = repository
        self.cropCatalog = cropCatalog
        self.isGuest = isGuest
    }

    var hasMore: Bool { nextCursor != nil }

    func onAppear() async {
        guard posts.isEmpty, !isLoading else { return }
        if !isGuest {
            await loadBoards()
        }
        await reload()
    }

    func loadBoards() async {
        // Board loading is non-fatal — the feed still works with just the "전체" chip if this fails.
        guard let fetchedBoards = try? await repository.fetchBoards() else { return }
        serverBoards = fetchedBoards
        mergeBoards(fetchedBoards)
    }

    func reload() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let page = try await repository.fetchPosts(query(cursor: nil))
            posts = filteredForAllCrops(page.items)
            nextCursor = page.nextCursor
        } catch {
            posts = []
            nextCursor = nil
            errorMessage = CommunityErrorMessage.text(for: error)
        }
    }

    func loadMoreIfNeeded(currentItem: CommunityPostSummary) async {
        guard let cursor = nextCursor, !isLoadingMore, !isLoading,
              currentItem.id == posts.last?.id else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await repository.fetchPosts(query(cursor: cursor))
            posts.append(contentsOf: filteredForAllCrops(page.items))
            nextCursor = page.nextCursor
        } catch {
            // Keep what we have; the row can retry on the next scroll trigger.
        }
    }

    // MARK: - Filters

    func selectPostType(_ type: CommunityPostType) async {
        guard postType != type else { return }
        postType = type
        await reload()
    }

    func selectCrop(_ cropId: UUID?) async {
        guard selectedCropId != cropId else { return }
        selectedCropId = cropId
        await reload()
    }

    func selectSort(_ sort: CommunityPostSort) async {
        guard self.sort != sort else { return }
        self.sort = sort
        await reload()
    }

    // MARK: - Board picker (작물 추가)

    /// Full crop catalog for the "작물 추가" picker. Empty on failure — the picker just shows nothing to add.
    func catalogCrops() async -> [Crop] {
        (try? await cropCatalog.fetchCrops()) ?? []
    }

    /// Crop categories for the picker's category tabs. Empty on failure — the picker just hides the tabs.
    func catalogCategories() async -> [CropCategory] {
        (try? await cropCatalog.fetchCategories()) ?? []
    }

    /// Adds crops chosen in the picker as session-local board chips and selects the first one.
    func addBoards(from crops: [Crop]) async {
        guard !crops.isEmpty else { return }
        let added = crops.map { CommunityBoard(cropId: $0.id, cropName: $0.name) }
        extraBoards.append(contentsOf: added)
        mergeBoards([])
        if let first = crops.first {
            await selectCrop(first.id)
        }
    }

    // MARK: - Like

    func toggleLike(_ post: CommunityPostSummary) async {
        do {
            let result = try await repository.toggleLike(postId: post.id)
            guard let index = posts.firstIndex(where: { $0.id == post.id }) else { return }
            posts[index].likedByMe = result.liked
            posts[index].likeCount = result.likeCount
        } catch {
            // Leave the row unchanged on failure.
        }
    }

    // MARK: - Helpers

    private func query(cursor: String?) -> CommunityPostQuery {
        CommunityPostQuery(cropId: selectedCropId, postType: postType, sort: sort, cursor: cursor)
    }

    /// "전체" (`selectedCropId == nil`) means every crop registered under 나의 작물, not the whole community —
    /// the backend has no multi-cropId filter, so restrict to `serverBoards` client-side. A guest has no
    /// 나의 작물 at all, so "전체" falls back to literally everything the server returned.
    private func filteredForAllCrops(_ items: [CommunityPostSummary]) -> [CommunityPostSummary] {
        guard selectedCropId == nil, !isGuest else { return items }
        let myCropIds = Set(serverBoards.map(\.cropId))
        return items.filter { myCropIds.contains($0.cropId) }
    }

    /// Merges server + session-added boards, de-duplicating by cropId and preserving insertion order.
    private func mergeBoards(_ serverBoards: [CommunityBoard]) {
        var seen = Set<UUID>()
        var merged: [CommunityBoard] = []
        for board in serverBoards + extraBoards where seen.insert(board.cropId).inserted {
            merged.append(board)
        }
        boards = merged
    }
}
