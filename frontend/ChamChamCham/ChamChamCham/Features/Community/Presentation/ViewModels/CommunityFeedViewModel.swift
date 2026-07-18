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
    private var memberId: UUID?

    private let repository: any CommunityRepository
    private let cropCatalog: any CropCatalogService
    private let extraBoardStore: ExtraCropBoardStore
    /// Browsing without an account — `/boards` still requires auth, and there's no 나의 작물 concept to
    /// restrict "전체" to, so both are skipped entirely for a guest.
    private let isGuest: Bool

    init(
        repository: any CommunityRepository,
        cropCatalog: any CropCatalogService,
        extraBoardStore: ExtraCropBoardStore,
        isGuest: Bool = false
    ) {
        self.repository = repository
        self.cropCatalog = cropCatalog
        self.extraBoardStore = extraBoardStore
        self.isGuest = isGuest
    }

    var hasMore: Bool { nextCursor != nil }

    /// `memberId` is nil for a guest — resolved by the caller from the SwiftData cache, which isn't
    /// available at init time (`@Environment` isn't populated yet there).
    func onAppear(memberId: UUID?) async {
        guard posts.isEmpty, !isLoading else { return }
        self.memberId = memberId
        if !isGuest {
            await loadBoards()
        }
        await reload()
    }

    func loadBoards() async {
        if let memberId {
            extraBoards = await extraBoardStore.load(memberId: memberId)
        }
        // Board loading is non-fatal — the feed still works with just the "전체" chip if this fails.
        guard let fetchedBoards = try? await repository.fetchBoards() else {
            mergeBoards()
            return
        }
        serverBoards = fetchedBoards
        mergeBoards()
    }

    func reload() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let page = try await repository.fetchPosts(query(cursor: nil))
            posts = page.items
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
            posts.append(contentsOf: page.items)
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

    /// Reconciles the picker's full current selection into `extraBoards` (persisted per member — see
    /// `ExtraCropBoardStore`), then selects the first newly added crop if there is one. Crops already
    /// backed by `serverBoards` (온보딩 작물) are left out of `extraBoards` — this ad hoc picker can't
    /// remove the member's registered crops, so unchecking one here has no effect on it.
    func addBoards(from crops: [Crop]) async {
        let serverCropIDs = Set(serverBoards.map(\.cropId))
        let previousExtraCropIDs = Set(extraBoards.map(\.cropId))
        extraBoards = crops
            .filter { !serverCropIDs.contains($0.id) }
            .map { CommunityBoard(cropId: $0.id, cropName: $0.name) }
        mergeBoards()
        if let memberId {
            await extraBoardStore.replace(with: extraBoards, memberId: memberId)
        }
        if let firstNew = crops.first(where: { !serverCropIDs.contains($0.id) && !previousExtraCropIDs.contains($0.id) }) {
            await selectCrop(firstNew.id)
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

    /// "전체" (`selectedCropId == nil`) means every crop registered under 나의 작물 (온보딩 crops) plus
    /// any added via the "+ 작물 추가" picker (`boards` is already that deduplicated union — see
    /// `mergeBoards`), not the whole community. The backend's `cropId` filter accepts repeated values,
    /// so this is sent server-side — filtering client-side after the fact broke pagination (a page whose
    /// 20 items happened to miss every one of the member's crops rendered zero rows, so the
    /// scroll-triggered `loadMoreIfNeeded` never fired again). A guest has no 나의 작물 to restrict to,
    /// so "전체" stays literally everything.
    private func query(cursor: String?) -> CommunityPostQuery {
        let cropIds: [UUID]
        if let selectedCropId {
            cropIds = [selectedCropId]
        } else if !isGuest {
            cropIds = boards.map(\.cropId)
        } else {
            cropIds = []
        }
        return CommunityPostQuery(cropIds: cropIds, postType: postType, sort: sort, cursor: cursor)
    }

    /// Merges `serverBoards` + `extraBoards`, de-duplicating by cropId and preserving insertion order.
    private func mergeBoards() {
        var seen = Set<UUID>()
        var merged: [CommunityBoard] = []
        for board in serverBoards + extraBoards where seen.insert(board.cropId).inserted {
            merged.append(board)
        }
        boards = merged
    }
}
