//
//  ProfileMainViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("ProfileMainViewModel")
struct ProfileMainViewModelTests {

    private func makeViewModel(
        profile: MyMemberProfile,
        boards: [CommunityBoard] = [],
        page: CommunityPostPage = CommunityPostPage(items: [], nextCursor: nil)
    ) -> (ProfileMainViewModel, StubCommunityRepository) {
        let community = StubCommunityRepository(boards: boards, page: page)
        let viewModel = ProfileMainViewModel(
            profileRepository: StubMemberProfileRepository(profile: profile),
            communityRepository: community
        )
        return (viewModel, community)
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
        #expect(!viewModel.canToggleCrops)
    }

    @Test("more than 3 crops collapse to 3 + overflow, and toggle expands/collapses back")
    func cropsCollapseAndExpand() async {
        let profile = MyPageFixtures.profile(crops: [
            MyPageFixtures.crop("인삼"), MyPageFixtures.crop("고추"), MyPageFixtures.crop("배추"),
            MyPageFixtures.crop("무"), MyPageFixtures.crop("파")
        ])
        let (viewModel, _) = makeViewModel(profile: profile)

        await viewModel.loadProfile()

        #expect(viewModel.canToggleCrops)
        #expect(viewModel.displayedCropNames.count == 3)
        #expect(viewModel.hiddenCropCount == 2)

        viewModel.cropsExpanded.toggle()
        #expect(viewModel.displayedCropNames.count == 5)
        #expect(viewModel.hiddenCropCount == 0)
        #expect(viewModel.canToggleCrops)

        viewModel.cropsExpanded.toggle()
        #expect(viewModel.displayedCropNames.count == 3)
        #expect(viewModel.hiddenCropCount == 2)
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
        #expect(viewModel.regionText == "전북 전주시")
    }

    @Test("my-posts tab queries mineOnly; liked tab queries likedOnly")
    func tabDrivesPostQueryScope() async {
        let (viewModel, community) = makeViewModel(profile: MyPageFixtures.profile())

        viewModel.selectedTabIndex = 0
        await viewModel.reloadPosts()
        var query = await community.lastQuery()
        #expect(query?.mineOnly == true)
        #expect(query?.likedOnly == false)

        viewModel.selectedTabIndex = 1
        await viewModel.reloadPosts()
        query = await community.lastQuery()
        #expect(query?.mineOnly == false)
        #expect(query?.likedOnly == true)
    }

    @Test("board filter forwards the selected crop ids into the query")
    func boardFilterAppliesCropId() async {
        let cropId = UUID()
        let boards = [CommunityBoard(cropId: cropId, cropName: "인삼")]
        let (viewModel, community) = makeViewModel(profile: MyPageFixtures.profile(), boards: boards)

        await viewModel.applyBoardFilter(cropIds: [cropId])

        #expect(viewModel.selectedBoardCropIds == [cropId])
        let query = await community.lastQuery()
        #expect(query?.cropIds == [cropId])
    }

    @Test("board filter forwards multiple selected crop ids into the query")
    func boardFilterAppliesMultipleCropIds() async {
        let firstId = UUID()
        let secondId = UUID()
        let boards = [
            CommunityBoard(cropId: firstId, cropName: "인삼"),
            CommunityBoard(cropId: secondId, cropName: "고추")
        ]
        let (viewModel, community) = makeViewModel(profile: MyPageFixtures.profile(), boards: boards)

        await viewModel.applyBoardFilter(cropIds: [firstId, secondId])

        #expect(viewModel.selectedBoardCropIds == [firstId, secondId])
        let query = await community.lastQuery()
        #expect(Set(query?.cropIds ?? []) == [firstId, secondId])
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
