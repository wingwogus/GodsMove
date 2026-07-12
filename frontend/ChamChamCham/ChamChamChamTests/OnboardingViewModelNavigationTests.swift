//
//  OnboardingViewModelNavigationTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/11/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("OnboardingViewModel navigation")
struct OnboardingViewModelNavigationTests {

    private func makeViewModel(startingAt step: OnboardingViewModel.Step, draft: OnboardingDraft = OnboardingDraft()) -> OnboardingViewModel {
        let store = OnboardingTestFactory.isolatedStore()
        let viewModel = OnboardingViewModel(
            store: store,
            onboardingRepository: FakeOnboardingRepository(),
            mediaUploadRepository: FakeMediaUploadRepository(),
            cropCatalogService: StubCropCatalogService(),
            memberProfileCache: StubMemberProfileCache()
        )
        viewModel.draft = draft
        viewModel.jump(to: step)
        return viewModel
    }

    private func makeViewModel(store: OnboardingDraftStore) -> OnboardingViewModel {
        OnboardingViewModel(
            store: store,
            onboardingRepository: FakeOnboardingRepository(),
            mediaUploadRepository: FakeMediaUploadRepository(),
            cropCatalogService: StubCropCatalogService(),
            memberProfileCache: StubMemberProfileCache()
        )
    }

    @Test("saved draft asks before restoring instead of auto-jumping")
    func savedDraftRequiresUserChoiceBeforeRestore() {
        var draft = OnboardingTestFactory.validDraft()
        draft.farmName = "저장된농장"
        let store = OnboardingTestFactory.isolatedStore()
        store.save(OnboardingDraftSnapshot(step: .farmLocation, draft: draft))

        let viewModel = makeViewModel(store: store)

        #expect(viewModel.currentStep == .landing)
        #expect(viewModel.shouldShowResumePrompt)
        #expect(viewModel.draft.farmName == "")
    }

    @Test("resume saved draft applies the saved step and draft")
    func resumeSavedDraftAppliesSnapshot() {
        var draft = OnboardingTestFactory.validDraft()
        draft.farmName = "저장된농장"
        let store = OnboardingTestFactory.isolatedStore()
        store.save(OnboardingDraftSnapshot(step: .cropSelection, draft: draft))
        let viewModel = makeViewModel(store: store)

        viewModel.resumeSavedDraft()

        #expect(viewModel.currentStep == .cropSelection)
        #expect(!viewModel.shouldShowResumePrompt)
        #expect(viewModel.draft.farmName == "저장된농장")
    }

    @Test("discard saved draft clears snapshot and starts from basic profile")
    func discardSavedDraftStartsFresh() {
        let store = OnboardingTestFactory.isolatedStore()
        store.save(OnboardingDraftSnapshot(step: .cropSelection, draft: OnboardingTestFactory.validDraft()))
        let viewModel = makeViewModel(store: store)

        viewModel.discardSavedDraftAndStartOver()

        #expect(viewModel.currentStep == .basicProfile)
        #expect(!viewModel.shouldShowResumePrompt)
        #expect(viewModel.draft.name == "")
        #expect(store.load() == nil)
    }

    @Test("authentication continues to basic profile only when no saved draft is pending")
    func continueAfterAuthenticationWithoutSavedDraft() {
        let viewModel = makeViewModel(store: OnboardingTestFactory.isolatedStore())

        viewModel.continueAfterAuthentication()

        #expect(viewModel.currentStep == .basicProfile)
        #expect(!viewModel.shouldShowResumePrompt)
    }

    @Test("authentication keeps landing visible when saved draft choice is pending")
    func continueAfterAuthenticationWithSavedDraftWaitsForChoice() {
        let store = OnboardingTestFactory.isolatedStore()
        store.save(OnboardingDraftSnapshot(step: .farmLocation, draft: OnboardingTestFactory.validDraft()))
        let viewModel = makeViewModel(store: store)

        viewModel.continueAfterAuthentication()

        #expect(viewModel.currentStep == .landing)
        #expect(viewModel.shouldShowResumePrompt)
    }

    @Test("Figma input step order is basic profile, farm location, crop selection, completion")
    func figmaInputStepOrder() {
        let viewModel = makeViewModel(startingAt: .basicProfile)

        viewModel.goNext()
        #expect(viewModel.currentStep == .farmLocation)

        viewModel.goNext()
        #expect(viewModel.currentStep == .cropSelection)

        viewModel.goNext()
        #expect(viewModel.currentStep == .complete)
    }

    @Test("adding a farm from completion creates a new active farm and returns to farm location")
    func addFarmFromCompletion() {
        var draft = OnboardingTestFactory.validDraft()
        draft.farmName = "첫번째농장"
        let viewModel = makeViewModel(startingAt: .complete, draft: draft)

        viewModel.addFarmFromCompletion()

        #expect(viewModel.currentStep == .farmLocation)
        #expect(viewModel.draft.farms.count == 2)
        #expect(viewModel.draft.activeFarmIndex == 1)
        #expect(viewModel.draft.activeFarm.farmName == "")
        #expect(viewModel.draft.farms[0].farmName == "첫번째농장")
    }

    @Test("crop selection toggles the active farm and enforces a maximum of five")
    func cropSelectionLimit() {
        let viewModel = makeViewModel(startingAt: .cropSelection)
        let cropIDs = (0..<6).map { _ in UUID() }

        for cropID in cropIDs.prefix(5) {
            #expect(viewModel.toggleCropSelection(cropID) == .selected)
        }

        #expect(viewModel.draft.cropIDs == Array(cropIDs.prefix(5)))
        #expect(viewModel.toggleCropSelection(cropIDs[5]) == .selectionLimitReached)
        #expect(viewModel.draft.cropIDs == Array(cropIDs.prefix(5)))
        #expect(viewModel.toggleCropSelection(cropIDs[2]) == .deselected)
        #expect(viewModel.draft.cropIDs == [cropIDs[0], cropIDs[1], cropIDs[3], cropIDs[4]])
    }
}
