//
//  OnboardingFigmaStateTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("Onboarding Figma states")
struct OnboardingFigmaStateTests {

    @Test("basic profile exposes Figma field-level required errors")
    func basicProfileRequiredErrors() {
        let viewModel = makeOnboardingViewModel()
        viewModel.draft.managementType = .agriculturalIndividual

        let errors = viewModel.basicProfileValidationErrors

        #expect(errors[.name] == "이름은 필수로 입력해주세요.")
        #expect(errors[.nickname] == "닉네임은 필수로 입력해주세요.")
        #expect(errors[.phone] == "연락처는 필수로 입력해주세요.")
        #expect(errors[.birthDate] == "생년월일은 필수로 입력해주세요.")
        #expect(errors[.experienceYears] == "귀농 년차는 필수로 입력해주세요.")
        #expect(errors[.managementType] == nil)
        #expect(!viewModel.canProceedFromBasicProfile)
    }

    @Test("basic profile validation passes only when every required field is present")
    func basicProfileValidationPassesWhenComplete() {
        let viewModel = makeOnboardingViewModel()
        viewModel.draft.name = "이름 입력 완료"
        viewModel.draft.nickname = "닉네임 입력 완료"
        viewModel.draft.phone = "000-0000-0000"
        viewModel.draft.birthDate = Date(timeIntervalSince1970: 0)
        viewModel.draft.experienceYears = 2
        viewModel.draft.managementType = .agriculturalIndividual

        #expect(viewModel.basicProfileValidationErrors.isEmpty)
        #expect(viewModel.canProceedFromBasicProfile)
    }

    @Test("farm location required-input errors match the captured missing states")
    func farmLocationRequiredErrors() {
        let viewModel = FarmLocationViewModel()

        #expect(viewModel.requiredInputError(farmName: "") == "주소지와 농지명은 필수로 입력해주세요.")

        viewModel.selectedAddress = JusoAddress(
            roadAddrPart1: "도로명 주소 입력 완료",
            jibunAddr: "지번 주소",
            bdNm: ""
        )
        #expect(viewModel.requiredInputError(farmName: "") == "농지명은 필수로 입력해주세요.")
        #expect(viewModel.requiredInputError(farmName: "농지명 입력 완료") == nil)

        viewModel.selectedAddress = nil
        #expect(viewModel.requiredInputError(farmName: "농지명 입력 완료") == "주소지는 필수로 입력해주세요.")
    }

    @Test("date field displays the Figma yyyy.mm.dd format")
    func dateFieldUsesFigmaDisplayFormat() throws {
        let components = DateComponents(calendar: Calendar(identifier: .gregorian), year: 1996, month: 7, day: 12)
        let date = try #require(components.date)

        #expect(AppDateField.formatter.string(from: date) == "1996.07.12")
    }

    @Test("onboarding progress maps to the product flow steps")
    func onboardingProgressFractions() {
        #expect(OnboardingProgressBar.progressFraction(for: .basicProfile) == 0.25)
        #expect(OnboardingProgressBar.progressFraction(for: .farmLocation) == 0.5)
        #expect(OnboardingProgressBar.progressFraction(for: .cropSelection) == 0.75)
        #expect(OnboardingProgressBar.progressFraction(for: .complete) == 1.0)
    }

    private func makeOnboardingViewModel() -> OnboardingViewModel {
        OnboardingViewModel(
            store: OnboardingTestFactory.isolatedStore(),
            onboardingRepository: FakeOnboardingRepository(),
            mediaUploadRepository: FakeMediaUploadRepository(),
            cropCatalogService: StubCropCatalogService(),
            memberProfileCache: StubMemberProfileCache(),
            pendingFarmSyncService: PendingFarmSyncService(
                store: OnboardingTestFactory.isolatedPendingFarmStore(),
                repository: FakeFarmRepository()
            )
        )
    }
}
