//
//  ProfileBasicInfoViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("ProfileBasicInfoViewModel")
struct ProfileBasicInfoViewModelTests {

    @Test("prefills form fields from the profile")
    func prefillsFromProfile() async {
        let profile = MyPageFixtures.profile(
            name: "장윤서",
            nickname: "인삼왕",
            phone: "010-1234-5678",
            birthDate: "1990-01-02",
            experienceLevel: 2,
            managementType: "AGRICULTURAL_CORPORATION"
        )
        let viewModel = ProfileBasicInfoViewModel(
            repository: StubMemberProfileRepository(profile: profile),
            mediaRepository: FakeMediaUploadRepository(),
            farmRepository: StubFarmRepository()
        )

        await viewModel.load()

        #expect(viewModel.name == "장윤서")
        #expect(viewModel.nickname == "인삼왕")
        #expect(viewModel.phone == "010-1234-5678")
        #expect(viewModel.experienceYears == 2)
        #expect(viewModel.managementType == .agriculturalCorporation)
        #expect(viewModel.birthDate != nil)
    }

    @Test("cannot save until required fields are valid; nickname stays optional")
    func validationGatesSaving() async {
        let profile = MyPageFixtures.profile(
            phone: "", birthDate: nil, experienceLevel: nil, managementType: "AGRICULTURAL_INDIVIDUAL"
        )
        let viewModel = ProfileBasicInfoViewModel(
            repository: StubMemberProfileRepository(profile: profile),
            mediaRepository: FakeMediaUploadRepository(),
            farmRepository: StubFarmRepository()
        )
        await viewModel.load()

        #expect(!viewModel.canSave)

        viewModel.phone = "010-0000-0000"
        viewModel.birthDate = Date(timeIntervalSince1970: 0)
        viewModel.experienceYears = 1
        viewModel.nickname = "" // optional

        #expect(viewModel.canSave)
    }

    @Test("validation errors surface only after a save attempt")
    func errorsAfterAttempt() async {
        let profile = MyPageFixtures.profile(phone: "", birthDate: nil, experienceLevel: nil)
        let viewModel = ProfileBasicInfoViewModel(
            repository: StubMemberProfileRepository(profile: profile),
            mediaRepository: FakeMediaUploadRepository(),
            farmRepository: StubFarmRepository()
        )
        await viewModel.load()

        #expect(viewModel.phoneError == nil)

        let saved = await viewModel.save()
        #expect(saved == false)
        #expect(viewModel.phoneError != nil)
        #expect(viewModel.birthDateError != nil)
        #expect(viewModel.experienceError != nil)
    }

    @Test("save builds the update request preserving name and mapping enums/date")
    func saveBuildsRequest() async {
        let profile = MyPageFixtures.profile(
            name: "장윤서",
            nickname: "인삼왕",
            phone: "010-1234-5678",
            birthDate: "1990-01-02",
            experienceLevel: 2,
            managementType: "AGRICULTURAL_INDIVIDUAL"
        )
        let repository = StubMemberProfileRepository(profile: profile)
        let viewModel = ProfileBasicInfoViewModel(
            repository: repository,
            mediaRepository: FakeMediaUploadRepository(),
            farmRepository: StubFarmRepository()
        )
        await viewModel.load()
        viewModel.managementType = .nonRegisteredFarmer

        let saved = await viewModel.save()
        #expect(saved)

        let request = await repository.lastUpdate()
        #expect(request?.name == "장윤서")               // 이름 수정 불가 → 기존 값 유지
        #expect(request?.phone == "010-1234-5678")
        #expect(request?.birthDate == "1990-01-02")
        #expect(request?.experienceLevel == 2)
        #expect(request?.managementType == "NON_REGISTERED_FARMER")
        #expect(request?.profileMediaId == nil)          // 사진을 새로 고르지 않았다면 변경 없음
    }

    @Test("save resends the member's current farms, since the backend requires them on every profile update")
    func saveIncludesCurrentFarms() async {
        let profile = MyPageFixtures.profile(
            phone: "010-1234-5678", birthDate: "1990-01-02", experienceLevel: 2
        )
        let farmId = UUID()
        let cropId = UUID()
        let repository = StubMemberProfileRepository(profile: profile)
        let viewModel = ProfileBasicInfoViewModel(
            repository: repository,
            mediaRepository: FakeMediaUploadRepository(),
            farmRepository: StubFarmRepository(farms: [
                MyPageFixtures.standaloneFarm(id: farmId, crops: [MyPageFixtures.cropResponse(id: cropId)])
            ])
        )
        await viewModel.load()

        let saved = await viewModel.save()
        #expect(saved)

        let request = await repository.lastUpdate()
        #expect(request?.farms.count == 1)
        #expect(request?.farms.first?.farmId == farmId)
        #expect(request?.farms.first?.cropIds == [cropId])
    }

    @Test("picking a photo uploads it and includes the mediaId on save")
    func pickImageUploadsAndSaves() async {
        let profile = MyPageFixtures.profile(
            phone: "010-1234-5678", birthDate: "1990-01-02", experienceLevel: 2
        )
        let mediaId = UUID()
        let repository = StubMemberProfileRepository(profile: profile)
        let viewModel = ProfileBasicInfoViewModel(
            repository: repository,
            mediaRepository: FakeMediaUploadRepository(successMediaId: mediaId),
            farmRepository: StubFarmRepository()
        )
        await viewModel.load()

        await viewModel.pickImage(Data([0x01, 0x02]))

        #expect(viewModel.previewImageData == Data([0x01, 0x02]))
        #expect(viewModel.imageErrorMessage == nil)
        #expect(!viewModel.isUploadingImage)

        let saved = await viewModel.save()
        #expect(saved)

        let request = await repository.lastUpdate()
        #expect(request?.profileMediaId == mediaId)
    }

    @Test("a failed photo upload reverts the preview and surfaces an error")
    func pickImageUploadFailureReverts() async {
        let profile = MyPageFixtures.profile()
        let viewModel = ProfileBasicInfoViewModel(
            repository: StubMemberProfileRepository(profile: profile),
            mediaRepository: FakeMediaUploadRepository(fails: true),
            farmRepository: StubFarmRepository()
        )
        await viewModel.load()

        await viewModel.pickImage(Data([0x01]))

        #expect(viewModel.previewImageData == nil)
        #expect(viewModel.imageErrorMessage != nil)
    }
}
