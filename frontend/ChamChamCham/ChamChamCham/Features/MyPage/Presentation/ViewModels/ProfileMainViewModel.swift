//
//  ProfileMainViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Observation

/// Drives the profile main screen: the member header, the 나의 게시물 / 좋아요 누른 글 tabs, the
/// board (crop) filter, and the cursor-paginated post list. Posts come from the community feed API
/// with `mineOnly` / `likedOnly` per the selected tab.
@Observable
@MainActor
final class ProfileMainViewModel {
    enum ContentTab: Int, CaseIterable {
        case myPosts = 0
        case likedPosts = 1
    }

    // Profile header
    private(set) var profile: MyMemberProfile?
    private(set) var isLoadingProfile = false
    private(set) var profileErrorMessage: String?

    // Post list
    private(set) var posts: [CommunityPostSummary] = []
    private(set) var isLoadingPosts = false
    private(set) var isLoadingMore = false
    private(set) var postsErrorMessage: String?
    private var nextCursor: String?
    private var hasMorePosts = true

    // Board filter
    private(set) var boards: [CommunityBoard] = []
    private(set) var isLoadingBoards = false
    private(set) var boardsErrorMessage: String?

    // UI state
    var selectedTabIndex = 0
    var cropsExpanded = false
    private(set) var selectedBoardCropId: UUID?

    @ObservationIgnored private let profileRepository: any MemberProfileRepository
    @ObservationIgnored private let communityRepository: any CommunityRepository

    init(
        profileRepository: any MemberProfileRepository,
        communityRepository: any CommunityRepository
    ) {
        self.profileRepository = profileRepository
        self.communityRepository = communityRepository
    }

    var currentTab: ContentTab { ContentTab(rawValue: selectedTabIndex) ?? .myPosts }

    // MARK: - Load

    func onAppear() async {
        async let profileTask: Void = profile == nil ? loadProfile() : ()
        async let postsTask: Void = posts.isEmpty ? reloadPosts() : ()
        _ = await (profileTask, postsTask)
    }

    func loadProfile() async {
        isLoadingProfile = true
        profileErrorMessage = nil
        defer { isLoadingProfile = false }
        do {
            profile = try await profileRepository.fetchMyProfile()
        } catch {
            profileErrorMessage = "프로필을 불러오지 못했어요. 다시 시도해주세요."
        }
    }

    /// Call when the tab binding changes; `currentTab` already reflects the new index.
    func onTabChanged() async {
        await reloadPosts()
    }

    func loadBoardsIfNeeded() async {
        guard boards.isEmpty, !isLoadingBoards else { return }
        isLoadingBoards = true
        boardsErrorMessage = nil
        defer { isLoadingBoards = false }
        do {
            boards = try await communityRepository.fetchBoards()
        } catch {
            boardsErrorMessage = "게시판을 불러오지 못했어요."
        }
    }

    func applyBoardFilter(cropId: UUID?) async {
        selectedBoardCropId = cropId
        await reloadPosts()
    }

    /// Boards split for the 게시판 선택 sheet. `진행중인 작물` = boards matching the member's own
    /// crops (`profile.crops`); `기타 작물` = the rest. NOTE: the boards API has no active/other flag,
    /// so this split is derived client-side from the member profile — confirm the intended grouping
    /// with the backend/designer.
    var activeBoards: [CommunityBoard] {
        let memberCropIds = Set(profile?.crops.map(\.cropId) ?? [])
        return boards.filter { memberCropIds.contains($0.cropId) }
    }

    var otherBoards: [CommunityBoard] {
        let memberCropIds = Set(profile?.crops.map(\.cropId) ?? [])
        return boards.filter { !memberCropIds.contains($0.cropId) }
    }

    var selectedBoardName: String? {
        guard let selectedBoardCropId else { return nil }
        return boards.first(where: { $0.cropId == selectedBoardCropId })?.cropName
    }

    func reloadPosts() async {
        isLoadingPosts = true
        postsErrorMessage = nil
        nextCursor = nil
        hasMorePosts = true
        defer { isLoadingPosts = false }
        do {
            let page = try await communityRepository.fetchPosts(makeQuery(cursor: nil))
            posts = page.items
            nextCursor = page.nextCursor
            hasMorePosts = page.nextCursor != nil
        } catch {
            posts = []
            postsErrorMessage = "게시물을 불러오지 못했어요. 다시 시도해주세요."
        }
    }

    func loadMoreIfNeeded(currentItem: CommunityPostSummary) async {
        guard hasMorePosts, !isLoadingMore, !isLoadingPosts else { return }
        guard currentItem.id == posts.last?.id else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await communityRepository.fetchPosts(makeQuery(cursor: nextCursor))
            posts.append(contentsOf: page.items)
            nextCursor = page.nextCursor
            hasMorePosts = page.nextCursor != nil
        } catch {
            // Keep the loaded page; the row can be retried on next scroll.
            hasMorePosts = false
        }
    }

    func toggleLike(_ post: CommunityPostSummary) async {
        do {
            let result = try await communityRepository.toggleLike(postId: post.id)
            guard let index = posts.firstIndex(where: { $0.id == post.id }) else { return }
            posts[index].likedByMe = result.liked
            posts[index].likeCount = result.likeCount
        } catch {
            // Ignore; the row keeps its previous state.
        }
    }

    // MARK: - Derived

    /// Crop badge labels for the header, honoring the expand/collapse rule
    /// (`≤3` all / `>3` first 3 + `외 n종`, tap `외 n종` to expand).
    var displayedCropNames: [String] {
        let names = profile?.crops.map(\.cropName) ?? []
        if cropsExpanded || names.count <= 3 { return names }
        return Array(names.prefix(3))
    }

    var hiddenCropCount: Int {
        let total = profile?.crops.count ?? 0
        guard !cropsExpanded, total > 3 else { return 0 }
        return total - 3
    }

    /// Whether the expand/collapse toggle should be shown at all (more than 3 crops on the profile).
    var canToggleCrops: Bool {
        (profile?.crops.count ?? 0) > 3
    }

    var experienceText: String? {
        guard let level = profile?.experienceLevel else { return nil }
        return "\(level)년차"
    }

    var regionText: String? {
        profile?.farms.first?.displayRegion
    }

    private func makeQuery(cursor: String?) -> CommunityPostQuery {
        CommunityPostQuery(
            cropId: selectedBoardCropId,
            likedOnly: currentTab == .likedPosts,
            mineOnly: currentTab == .myPosts,
            cursor: cursor
        )
    }
}
