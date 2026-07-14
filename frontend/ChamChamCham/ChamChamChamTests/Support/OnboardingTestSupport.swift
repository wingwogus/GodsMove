//
//  OnboardingTestSupport.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
@testable import ChamChamCham

// MARK: - Fakes

/// Actors, not `@MainActor` classes: these witness `nonisolated async` protocol requirements, so a `@MainActor`
/// class body can't mutate its own recorded state. Failure is modelled with plain `Sendable` values (Bool/Int)
/// rather than a stored `Result<_, Error>`, which wouldn't be `Sendable` to hand across the actor boundary.
actor FakeMediaUploadRepository: MediaUploadRepository {
    private(set) var callCount = 0
    private(set) var lastImageData: Data?
    private let successMediaId: UUID
    private let fails: Bool

    init(successMediaId: UUID = UUID(), fails: Bool = false) {
        self.successMediaId = successMediaId
        self.fails = fails
    }

    func uploadProfileImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        callCount += 1
        lastImageData = imageData
        if fails { throw FakeUploadError() }
        return OnboardingTestFactory.uploadedImage(mediaId: successMediaId)
    }

    func uploadCommunityImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        callCount += 1
        lastImageData = imageData
        if fails { throw FakeUploadError() }
        return OnboardingTestFactory.uploadedImage(mediaId: successMediaId)
    }

    func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        callCount += 1
        lastImageData = imageData
        if fails { throw FakeUploadError() }
        return OnboardingTestFactory.uploadedImage(mediaId: successMediaId)
    }
}

actor FakeOnboardingRepository: OnboardingRepository {
    private(set) var callCount = 0
    private(set) var lastDraft: OnboardingDraft?
    /// The number of leading calls that should fail before completion starts succeeding — models a transient
    /// server error the user retries past.
    private let failFirst: Int

    init(failFirst: Int = 0) {
        self.failFirst = failFirst
    }

    func completeOnboarding(_ draft: OnboardingDraft) async throws -> OnboardingCompleteResponseDTO {
        callCount += 1
        lastDraft = draft
        if callCount <= failFirst { throw FakeUploadError() }
        return OnboardingTestFactory.sampleResponse()
    }
}

actor FakeFarmRepository: FarmRepository {
    private(set) var createdNames: [String] = []
    private var callCount = 0
    private let failAtCall: Int?

    init(failAtCall: Int? = nil) {
        self.failAtCall = failAtCall
    }

    func listFarms() async throws -> [StandaloneFarmResponseDTO] { [] }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        callCount += 1
        createdNames.append(request.name)
        if callCount == failAtCall { throw FakeUploadError() }
        return OnboardingTestFactory.standaloneFarmResponse(name: request.name)
    }

    func deleteFarm(id: UUID) async throws {}
}

@MainActor
struct StubCropCatalogService: CropCatalogService {
    func fetchCrops() async throws -> [Crop] { [] }
    func fetchCrops(categoryCode: String) async throws -> [Crop] { [] }
    func fetchCategories() async throws -> [CropCategory] { [] }
}

@MainActor
final class StubMemberProfileCache: MemberProfileCache {
    private(set) var savedMember: MemberProfileResponseDTO?

    func save(member: MemberProfileResponseDTO, onboarding: OnboardingResponseDTO) -> CachedMemberProfile {
        savedMember = member
        return CachedMemberProfile(
            id: member.id,
            email: member.email,
            name: member.name,
            nickname: member.nickname,
            phone: member.phone,
            birthDateRaw: member.birthDate,
            experienceLevel: member.experienceLevel,
            managementTypeRaw: member.managementType,
            profileImageUrl: member.profileImageUrl,
            onboardingStatusRaw: onboarding.status.rawValue,
            missingFieldsRaw: onboarding.missingFields,
            updatedAt: Date()
        )
    }
}

struct FakeUploadError: Error {}

// MARK: - Factory

enum OnboardingTestFactory {
    /// A draft with every field the submission requires already filled — individual tests mutate only what they
    /// exercise (e.g. add a photo, blank a required field).
    static func validDraft() -> OnboardingDraft {
        var draft = OnboardingDraft()
        draft.name = "홍길동"
        draft.nickname = "길동"
        draft.phone = "010-1234-5678"
        draft.birthDate = DateComponents(calendar: .init(identifier: .gregorian), year: 1990, month: 1, day: 1).date
        draft.experienceYears = 3
        draft.managementType = .agriculturalIndividual
        draft.cropIDs = [UUID()]
        draft.farmName = "길동농장"
        draft.farmRoadAddress = "서울시 강남구 테헤란로 1"
        draft.farmJibunAddress = "서울시 강남구 역삼동 1"
        draft.farmLatitude = 35.8465
        draft.farmLongitude = 127.1292
        return draft
    }

    static func sampleResponse() -> OnboardingCompleteResponseDTO {
        OnboardingCompleteResponseDTO(
            member: MemberProfileResponseDTO(
                id: UUID(),
                email: "member@example.com",
                name: "홍길동",
                phone: "010-1234-5678",
                birthDate: "1990-01-01",
                nickname: "길동",
                experienceLevel: 3,
                managementType: "AGRICULTURAL_INDIVIDUAL",
                profileImageUrl: "https://res.cloudinary.com/example.jpg"
            ),
            farm: FarmResponseDTO(
                id: UUID(),
                name: "길동농장",
                roadAddress: "서울시 강남구 테헤란로 1",
                jibunAddress: nil,
                latitude: 35.8465,
                longitude: 127.1292,
                pnu: nil,
                landCategory: nil,
                areaSqm: nil,
                areaIsManualEntry: false,
                boundaryCoordinates: [],
                dataSource: .onboardingJusoVWorld
            ),
            crops: [],
            onboarding: OnboardingResponseDTO(status: .complete, missingFields: [])
        )
    }

    static func uploadedImage(mediaId: UUID) -> UploadedImageResponseDTO {
        UploadedImageResponseDTO(mediaId: mediaId, imageUrl: "https://res.cloudinary.com/\(mediaId).jpg", status: "TEMP")
    }

    static func standaloneFarmResponse(name: String) -> StandaloneFarmResponseDTO {
        StandaloneFarmResponseDTO(
            farmId: UUID(),
            name: name,
            roadAddress: "전북 전주시 예시로 1",
            jibunAddress: nil,
            latitude: 35.8,
            longitude: 127.1,
            pnu: nil,
            landCategory: nil,
            areaSqm: nil,
            areaIsManualEntry: false,
            boundaryCoordinates: [],
            dataSource: .onboardingJusoVWorld,
            crops: []
        )
    }

    /// A store rooted in a unique temp directory + UserDefaults suite, so parallel tests never share saved images
    /// or `clear()` each other's state.
    @MainActor
    static func isolatedStore() -> OnboardingDraftStore {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let defaults = UserDefaults(suiteName: "test-\(UUID().uuidString)")!
        return OnboardingDraftStore(defaults: defaults, baseDirectory: dir)
    }

    static func isolatedPendingFarmStore() -> PendingFarmStore {
        let defaults = UserDefaults(suiteName: "pending-farm-test-\(UUID().uuidString)")!
        return PendingFarmStore(defaults: defaults)
    }
}
