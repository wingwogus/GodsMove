//
//  MyPageTestSupport.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/13/26.
//

import Foundation
@testable import ChamChamCham

enum MyPageTestError: Error { case unused }

// MARK: - Factories

enum MyPageFixtures {
    static func crop(_ name: String, id: UUID = UUID()) -> MemberCropProfile {
        MemberCropProfile(cropId: id, cropName: name)
    }

    static func farm(
        id: UUID = UUID(),
        name: String = "행복농장",
        region: String? = "전북 전주시"
    ) -> MyMemberFarm {
        MyMemberFarm(
            farmId: id,
            name: name,
            roadAddress: "전북 전주시 완산구 테스트로 1",
            jibunAddress: nil,
            displayRegion: region
        )
    }

    static func profile(
        name: String? = "장윤서",
        nickname: String? = "인삼왕",
        phone: String? = "010-0000-0000",
        birthDate: String? = "1990-01-02",
        experienceLevel: Int? = 2,
        managementType: String? = "AGRICULTURAL_INDIVIDUAL",
        farms: [MyMemberFarm] = [],
        crops: [MemberCropProfile] = []
    ) -> MyMemberProfile {
        MyMemberProfile(
            memberId: UUID(),
            email: "test@example.com",
            name: name,
            phone: phone,
            birthDate: birthDate,
            nickname: nickname,
            experienceLevel: experienceLevel,
            managementType: managementType,
            profileImageUrl: nil,
            farms: farms,
            crops: crops
        )
    }

    static func postSummary(id: UUID = UUID(), title: String = "타이틀") -> CommunityPostSummary {
        CommunityPostSummary(
            id: id,
            cropId: UUID(),
            cropName: "작물",
            postType: .general,
            title: title,
            bodyPreview: "캡션",
            thumbnailUrl: nil,
            author: CommunityAuthor(memberId: UUID(), nickname: "닉네임", profileImageUrl: nil),
            commentCount: 0,
            likeCount: 0,
            likedByMe: false,
            createdAt: Date(timeIntervalSince1970: 0)
        )
    }

    static func standaloneFarm(
        id: UUID = UUID(),
        name: String = "행복농장",
        crops: [CropResponseDTO] = []
    ) -> StandaloneFarmResponseDTO {
        StandaloneFarmResponseDTO(
            farmId: id,
            name: name,
            roadAddress: "전북 전주시 완산구 테스트로 1",
            jibunAddress: nil,
            latitude: 35.8,
            longitude: 127.1,
            pnu: nil,
            landCategory: nil,
            areaSqm: nil,
            areaIsManualEntry: true,
            boundaryCoordinates: [],
            dataSource: .onboardingJusoVWorld,
            crops: crops
        )
    }

    static func cropResponse(id: UUID = UUID(), name: String = "인삼") -> CropResponseDTO {
        CropResponseDTO(id: id, externalNo: 1, name: name, usePartCategory: "ROOT", usePartCategoryLabel: "뿌리")
    }
}

// MARK: - Stubs

actor StubMemberProfileRepository: MemberProfileRepository {
    private let profile: MyMemberProfile
    private var recordedUpdate: UpdateMyProfileRequestDTO?

    init(profile: MyMemberProfile) {
        self.profile = profile
    }

    func fetchMyProfile() async throws -> MyMemberProfile { profile }

    func fetchPublicProfile(memberId: UUID) async throws -> PublicMemberProfile {
        PublicMemberProfile(
            memberId: memberId,
            nickname: profile.nickname,
            experienceLevel: profile.experienceLevel,
            managementType: profile.managementType,
            profileImageUrl: profile.profileImageUrl,
            farms: [],
            crops: profile.crops
        )
    }

    func updateMyProfile(_ request: UpdateMyProfileRequestDTO) async throws -> MyMemberProfile {
        recordedUpdate = request
        return profile
    }

    func lastUpdate() -> UpdateMyProfileRequestDTO? { recordedUpdate }
}

actor StubCommunityRepository: CommunityRepository {
    private let boards: [CommunityBoard]
    private let page: CommunityPostPage
    private var recordedQuery: CommunityPostQuery?

    init(boards: [CommunityBoard] = [], page: CommunityPostPage = CommunityPostPage(items: [], nextCursor: nil)) {
        self.boards = boards
        self.page = page
    }

    func fetchBoards() async throws -> [CommunityBoard] { boards }

    func fetchPosts(_ query: CommunityPostQuery) async throws -> CommunityPostPage {
        recordedQuery = query
        return page
    }

    func lastQuery() -> CommunityPostQuery? { recordedQuery }

    func fetchPostDetail(id: UUID) async throws -> CommunityPostDetail { throw MyPageTestError.unused }
    func createPost(cropId: UUID, postType: CommunityPostType, title: String, body: String, farmingRecordId: UUID?, mediaIds: [UUID]) async throws -> UUID { UUID() }
    func updatePost(id: UUID, cropId: UUID, postType: CommunityPostType, title: String, body: String, farmingRecordId: UUID?, mediaIds: [UUID]) async throws -> UUID { id }
    func deletePost(id: UUID) async throws {}
    func fetchComments(postId: UUID, cursor: String?, size: Int) async throws -> CommunityCommentPage { CommunityCommentPage(items: [], nextCursor: nil) }
    func createComment(postId: UUID, parentCommentId: UUID?, body: String, mediaId: UUID?) async throws -> UUID { UUID() }
    func deleteComment(id: UUID) async throws {}
    func toggleLike(postId: UUID) async throws -> LikeToggleResult { LikeToggleResult(liked: true, likeCount: 1) }
}

actor StubFarmRepository: FarmRepository {
    private let farms: [StandaloneFarmResponseDTO]
    private var recordedCreates: [SaveFarmRequestDTO] = []
    private var recordedDeletes: [UUID] = []

    init(farms: [StandaloneFarmResponseDTO] = []) {
        self.farms = farms
    }

    func listFarms() async throws -> [StandaloneFarmResponseDTO] { farms }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        recordedCreates.append(request)
        return MyPageFixtures.standaloneFarm(name: request.name)
    }

    func deleteFarm(id: UUID) async throws {
        recordedDeletes.append(id)
    }

    func creates() -> [SaveFarmRequestDTO] { recordedCreates }
    func deletes() -> [UUID] { recordedDeletes }
}

actor StubAuthRepository: AuthRepository {
    private var logoutCalls = 0
    private let logoutError: Error?
    private var withdrawCalls = 0
    private let withdrawError: Error?

    init(logoutError: Error? = nil, withdrawError: Error? = nil) {
        self.logoutError = logoutError
        self.withdrawError = withdrawError
    }

    func loginWithKakao(idToken: String, nonce: String, kakaoAccessToken: String?) async throws -> LoginResponseDTO { throw MyPageTestError.unused }
    func loginWithApple(identityToken: String, nonce: String, authorizationCode: String?, userIdentifier: String?) async throws -> LoginResponseDTO { throw MyPageTestError.unused }
    func loginWithNaver(accessToken: String) async throws -> LoginResponseDTO { throw MyPageTestError.unused }

    func logout() async throws {
        logoutCalls += 1
        if let logoutError { throw logoutError }
    }

    func withdraw() async throws {
        withdrawCalls += 1
        if let withdrawError { throw withdrawError }
    }

    func logoutCallCount() -> Int { logoutCalls }
    func withdrawCallCount() -> Int { withdrawCalls }
}
