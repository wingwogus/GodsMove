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
        let viewModel = ProfileBasicInfoViewModel(repository: StubMemberProfileRepository(profile: profile))

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
        let viewModel = ProfileBasicInfoViewModel(repository: StubMemberProfileRepository(profile: profile))
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
        let viewModel = ProfileBasicInfoViewModel(repository: StubMemberProfileRepository(profile: profile))
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
        let viewModel = ProfileBasicInfoViewModel(repository: repository)
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
    }
}
