//
//  MemberProfileViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Observation

/// Drives the "다른 회원 프로필" screen: the member header, the board (crop) filter, and the
/// cursor-paginated list of that member's posts. Unlike `ProfileMainViewModel`, there is no
/// my-posts/liked-posts tab — posts are always filtered to `memberId` via the community feed API's
/// `memberId` query parameter.
@Observable
@MainActor
final class MemberProfileViewModel {
    let memberId: UUID

    // Profile header
    private(set) var profile: PublicMemberProfile?
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
    var cropsExpanded = false
    private(set) var selectedBoardCropIds: Set<UUID> = []

    @ObservationIgnored private let profileRepository: any MemberProfileRepository
    @ObservationIgnored private let communityRepository: any CommunityRepository

    init(
        memberId: UUID,
        profileRepository: any MemberProfileRepository,
        communityRepository: any CommunityRepository
    ) {
        self.memberId = memberId
        self.profileRepository = profileRepository
        self.communityRepository = communityRepository
    }

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
            profile = try await profileRepository.fetchPublicProfile(memberId: memberId)
        } catch {
            profileErrorMessage = "프로필을 불러오지 못했어요. 다시 시도해주세요."
        }
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

    func applyBoardFilter(cropIds: Set<UUID>) async {
        selectedBoardCropIds = cropIds
        await reloadPosts()
    }

    /// Boards split for the 게시판 선택 sheet, mirroring `ProfileMainViewModel`: `진행중인 작물` =
    /// boards matching this member's own crops (`profile.crops`); `기타 작물` = the rest.
    var activeBoards: [CommunityBoard] {
        let memberCropIds = Set(profile?.crops.map(\.cropId) ?? [])
        return boards.filter { memberCropIds.contains($0.cropId) }
    }

    var otherBoards: [CommunityBoard] {
        let memberCropIds = Set(profile?.crops.map(\.cropId) ?? [])
        return boards.filter { !memberCropIds.contains($0.cropId) }
    }

    /// Filter chip label: the sole selected board's name, `"{name} 외 {n}개"` for multiple, or nil
    /// when nothing is selected (falls back to the "게시판 선택" placeholder).
    var selectedBoardName: String? {
        let names = boards.filter { selectedBoardCropIds.contains($0.cropId) }.map(\.cropName)
        guard let first = names.first else { return nil }
        return names.count == 1 ? first : "\(first) 외 \(names.count - 1)개"
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

    var experienceText: String? {
        guard let level = profile?.experienceLevel else { return nil }
        return "\(level)년차"
    }

    var regionText: String? {
        profile?.farms.first?.displayRegion
    }

    private func makeQuery(cursor: String?) -> CommunityPostQuery {
        CommunityPostQuery(
            cropIds: Array(selectedBoardCropIds),
            memberId: memberId,
            cursor: cursor
        )
    }
}
