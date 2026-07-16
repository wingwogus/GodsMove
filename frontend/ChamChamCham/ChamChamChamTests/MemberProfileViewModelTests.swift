//
//  MemberProfileViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("MemberProfileViewModel")
struct MemberProfileViewModelTests {

    private func makeViewModel(
        targetMemberId: UUID = UUID(),
        profile: MyMemberProfile,
        boards: [CommunityBoard] = [],
        page: CommunityPostPage = CommunityPostPage(items: [], nextCursor: nil)
    ) -> (MemberProfileViewModel, StubCommunityRepository) {
        let community = StubCommunityRepository(boards: boards, page: page)
        let viewModel = MemberProfileViewModel(
            memberId: targetMemberId,
            profileRepository: StubMemberProfileRepository(profile: profile),
            communityRepository: community
        )
        return (viewModel, community)
    }

    @Test("loads the target member's public profile, not the caller's own profile")
    func loadsPublicProfileForTargetMember() async {
        let targetMemberId = UUID()
        let profile = MyPageFixtures.profile(nickname: "인삼왕")
        let (viewModel, _) = makeViewModel(targetMemberId: targetMemberId, profile: profile)

        await viewModel.loadProfile()

        #expect(viewModel.profile?.memberId == targetMemberId)
        #expect(viewModel.profile?.nickname == "인삼왕")
    }

    @Test("post query filters by the target member id, with no mineOnly/likedOnly tab")
    func postQueryScopedToTargetMember() async {
        let targetMemberId = UUID()
        let (viewModel, community) = makeViewModel(targetMemberId: targetMemberId, profile: MyPageFixtures.profile())

        await viewModel.reloadPosts()

        let query = await community.lastQuery()
        #expect(query?.memberId == targetMemberId)
        #expect(query?.mineOnly == false)
        #expect(query?.likedOnly == false)
    }

    @Test("crop badges show all when 3 or fewer")
    func cropsAllShownWhenThreeOrFewer() async {
        let profile = MyPageFixtures.profile(crops: [
            MyPageFixtures.crop("인삼"), MyPageFixtures.crop("고추"), MyPageFixtures.crop("배추")
        ])
        let (viewModel, _) = makeViewModel(profile: profile)

        await viewModel.loadProfile()

        #expect(viewModel.displayedCropNames.count == 3)
        #expect(viewModel.hiddenCropCount == 0)
    }

    @Test("more than 3 crops collapse to 3 + overflow, and expand to all")
    func cropsCollapseAndExpand() async {
        let profile = MyPageFixtures.profile(crops: [
            MyPageFixtures.crop("인삼"), MyPageFixtures.crop("고추"), MyPageFixtures.crop("배추"),
            MyPageFixtures.crop("무"), MyPageFixtures.crop("파")
        ])
        let (viewModel, _) = makeViewModel(profile: profile)

        await viewModel.loadProfile()

        #expect(viewModel.displayedCropNames.count == 3)
        #expect(viewModel.hiddenCropCount == 2)

        viewModel.cropsExpanded = true
        #expect(viewModel.displayedCropNames.count == 5)
        #expect(viewModel.hiddenCropCount == 0)
    }

    @Test("experience and region text derive from the profile")
    func derivedHeaderText() async {
        let profile = MyPageFixtures.profile(
            experienceLevel: 3,
            farms: [MyPageFixtures.farm(region: "전북 전주시")]
        )
        let (viewModel, _) = makeViewModel(profile: profile)

        await viewModel.loadProfile()

        #expect(viewModel.experienceText == "3년차")
        // StubMemberProfileRepository.fetchPublicProfile always returns farms: [], so the
        // public-profile region text is nil even though the backing MyMemberProfile has one.
        #expect(viewModel.regionText == nil)
    }

    @Test("board filter forwards the selected crop id, keeping the member id filter")
    func boardFilterAppliesCropId() async {
        let targetMemberId = UUID()
        let cropId = UUID()
        let boards = [CommunityBoard(cropId: cropId, cropName: "인삼")]
        let (viewModel, community) = makeViewModel(targetMemberId: targetMemberId, profile: MyPageFixtures.profile(), boards: boards)

        await viewModel.applyBoardFilter(cropId: cropId)

        #expect(viewModel.selectedBoardCropId == cropId)
        let query = await community.lastQuery()
        #expect(query?.cropId == cropId)
        #expect(query?.memberId == targetMemberId)
    }

    @Test("board split classifies member crops as active, others as 기타")
    func boardSplitByMemberCrops() async {
        let activeId = UUID()
        let otherId = UUID()
        let profile = MyPageFixtures.profile(crops: [MyPageFixtures.crop("인삼", id: activeId)])
        let boards = [
            CommunityBoard(cropId: activeId, cropName: "인삼"),
            CommunityBoard(cropId: otherId, cropName: "고추")
        ]
        let (viewModel, _) = makeViewModel(profile: profile, boards: boards)

        await viewModel.loadProfile()
        await viewModel.loadBoardsIfNeeded()

        #expect(viewModel.activeBoards.map(\.cropId) == [activeId])
        #expect(viewModel.otherBoards.map(\.cropId) == [otherId])
    }
}
