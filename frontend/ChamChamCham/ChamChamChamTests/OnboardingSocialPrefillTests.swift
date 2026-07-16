//
//  OnboardingSocialPrefillTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/15/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("OnboardingViewModel social login prefill")
struct OnboardingSocialPrefillTests {

    private func makeViewModel(
        store: OnboardingDraftStore,
        memberProfileCache: StubMemberProfileCache
    ) -> OnboardingViewModel {
        OnboardingViewModel(
            store: store,
            onboardingRepository: FakeOnboardingRepository(),
            mediaUploadRepository: FakeMediaUploadRepository(),
            cropCatalogService: StubCropCatalogService(),
            memberProfileCache: memberProfileCache,
            pendingFarmSyncService: PendingFarmSyncService(
                store: OnboardingTestFactory.isolatedPendingFarmStore(),
                repository: FakeFarmRepository()
            )
        )
    }

    @Test("a fresh draft is prefilled from the cached member's social login data")
    func freshDraftPrefillsFromCachedMember() {
        let cache = StubMemberProfileCache()
        cache.currentMember = OnboardingTestFactory.cachedMember(
            name: "김철수",
            nickname: "철수",
            phone: "010-9999-8888",
            birthDateRaw: "1995-03-20"
        )

        let viewModel = makeViewModel(store: OnboardingTestFactory.isolatedStore(), memberProfileCache: cache)

        #expect(viewModel.draft.name == "김철수")
        #expect(viewModel.draft.nickname == "철수")
        #expect(viewModel.draft.phone == "010-9999-8888")
        #expect(viewModel.draft.birthDate == OnboardingCompleteRequestDTO.wireDateFormatter.date(from: "1995-03-20"))
    }

    @Test("no cached member leaves the draft empty, same as before prefill existed")
    func noCachedMemberLeavesDraftEmpty() {
        let viewModel = makeViewModel(store: OnboardingTestFactory.isolatedStore(), memberProfileCache: StubMemberProfileCache())

        #expect(viewModel.draft.name == "")
        #expect(viewModel.draft.nickname == "")
        #expect(viewModel.draft.phone == "")
        #expect(viewModel.draft.birthDate == nil)
    }

    @Test("only the fields the provider actually returned are prefilled")
    func partialCachedMemberPrefillsOnlyPresentFields() {
        let cache = StubMemberProfileCache()
        cache.currentMember = OnboardingTestFactory.cachedMember(
            name: "박영희",
            nickname: nil,
            phone: "010-1111-2222",
            birthDateRaw: nil
        )

        let viewModel = makeViewModel(store: OnboardingTestFactory.isolatedStore(), memberProfileCache: cache)

        #expect(viewModel.draft.name == "박영희")
        #expect(viewModel.draft.nickname == "")
        #expect(viewModel.draft.phone == "010-1111-2222")
        #expect(viewModel.draft.birthDate == nil)
    }

    @Test("a restorable saved draft is not overwritten by social prefill")
    func restorableDraftIsNotOverwrittenByPrefill() {
        var savedDraft = OnboardingTestFactory.validDraft()
        savedDraft.name = "저장된이름"
        let store = OnboardingTestFactory.isolatedStore()
        store.save(OnboardingDraftSnapshot(step: .farmLocation, draft: savedDraft))

        let cache = StubMemberProfileCache()
        cache.currentMember = OnboardingTestFactory.cachedMember(name: "소셜이름")

        let viewModel = makeViewModel(store: store, memberProfileCache: cache)

        #expect(viewModel.shouldShowResumePrompt)
        #expect(viewModel.draft.name == "")

        viewModel.resumeSavedDraft()
        #expect(viewModel.draft.name == "저장된이름")
    }

    @Test("discarding a saved draft and starting over re-seeds from the cached member")
    func discardAndStartOverReseedsFromCachedMember() {
        let store = OnboardingTestFactory.isolatedStore()
        store.save(OnboardingDraftSnapshot(step: .cropSelection, draft: OnboardingTestFactory.validDraft()))

        let cache = StubMemberProfileCache()
        cache.currentMember = OnboardingTestFactory.cachedMember(name: "소셜이름", nickname: "소셜닉네임")

        let viewModel = makeViewModel(store: store, memberProfileCache: cache)
        viewModel.discardSavedDraftAndStartOver()

        #expect(viewModel.currentStep == .basicProfile)
        #expect(viewModel.draft.name == "소셜이름")
        #expect(viewModel.draft.nickname == "소셜닉네임")
    }
}
