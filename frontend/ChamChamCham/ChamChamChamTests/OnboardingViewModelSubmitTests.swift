//
//  OnboardingViewModelSubmitTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("OnboardingViewModel.submit")
struct OnboardingViewModelSubmitTests {

    private struct Harness {
        let viewModel: OnboardingViewModel
        let store: OnboardingDraftStore
        let media: FakeMediaUploadRepository
        let onboarding: FakeOnboardingRepository
        let farm: FakeFarmRepository
        let pendingStore: PendingFarmStore
        let appState: AppState
    }

    /// Builds a view model over an isolated store. When `withPhoto` is true, a local photo file is saved and its
    /// name written onto the draft, so the submit flow has something to upload.
    private func makeHarness(
        withPhoto: Bool,
        uploadMediaId: UUID = UUID(),
        uploadFails: Bool = false,
        completionFailFirst: Int = 0,
        farmCount: Int = 1,
        farmFailAtCall: Int? = nil
    ) -> Harness {
        let store = OnboardingTestFactory.isolatedStore()
        var draft = OnboardingTestFactory.validDraft()
        if farmCount > 1 {
            let representativeFarm = draft.representativeFarm
            let extraFarms = (1..<farmCount).map { index in
                OnboardingFarmDraft(
                    cropIDs: [UUID()],
                    farmName: "추가농장\(index)",
                    farmRoadAddress: "전북 전주시 추가로 \(index)",
                    farmJibunAddress: "전북 전주시 추가동 \(index)",
                    farmLatitude: 35.8 + Double(index) / 100,
                    farmLongitude: 127.1 + Double(index) / 100
                )
            }
            draft.farms = [representativeFarm] + extraFarms
            draft.activeFarmIndex = draft.farms.count - 1
        }
        if withPhoto {
            draft.profileImageFileName = store.saveProfileImage(Data("fake-image".utf8))
        }
        store.save(OnboardingDraftSnapshot(step: .complete, draft: draft))

        let media = FakeMediaUploadRepository(successMediaId: uploadMediaId, fails: uploadFails)
        let onboarding = FakeOnboardingRepository(failFirst: completionFailFirst)
        let farm = FakeFarmRepository(failAtCall: farmFailAtCall)
        let pendingStore = OnboardingTestFactory.isolatedPendingFarmStore()
        let pendingFarmSyncService = PendingFarmSyncService(store: pendingStore, repository: farm)
        let viewModel = OnboardingViewModel(
            store: store,
            onboardingRepository: onboarding,
            mediaUploadRepository: media,
            cropCatalogService: StubCropCatalogService(),
            memberProfileCache: StubMemberProfileCache(),
            pendingFarmSyncService: pendingFarmSyncService
        )
        viewModel.resumeSavedDraft()
        return Harness(
            viewModel: viewModel,
            store: store,
            media: media,
            onboarding: onboarding,
            farm: farm,
            pendingStore: pendingStore,
            appState: AppState()
        )
    }

    @Test("no photo: completes without touching the media upload API")
    func completesWithoutPhoto() async {
        let harness = makeHarness(withPhoto: false)

        await harness.viewModel.submit(appState: harness.appState)

        let mediaCalls = await harness.media.callCount
        let onboardingCalls = await harness.onboarding.callCount
        let sentDraft = await harness.onboarding.lastDraft
        #expect(mediaCalls == 0)
        #expect(onboardingCalls == 1)
        #expect(sentDraft?.profileMediaId == nil)
        #expect(harness.appState.isOnboarded)
        #expect(harness.viewModel.submissionState == .idle)
    }

    @Test("with photo: uploads first, then submits with the returned mediaId")
    func uploadsThenCompletes() async {
        let mediaId = UUID()
        let harness = makeHarness(withPhoto: true, uploadMediaId: mediaId)

        await harness.viewModel.submit(appState: harness.appState)

        let mediaCalls = await harness.media.callCount
        let sentDraft = await harness.onboarding.lastDraft
        #expect(mediaCalls == 1)
        #expect(harness.viewModel.draft.profileMediaId == mediaId)
        #expect(sentDraft?.profileMediaId == mediaId)
        #expect(harness.appState.isOnboarded)
    }

    @Test("photo upload failure: surfaces .photoUploadFailed and skips completion")
    func photoUploadFailureBlocksCompletion() async {
        let harness = makeHarness(withPhoto: true, uploadFails: true)

        await harness.viewModel.submit(appState: harness.appState)

        let onboardingCalls = await harness.onboarding.callCount
        #expect(onboardingCalls == 0)
        #expect(!harness.appState.isOnboarded)
        if case .photoUploadFailed = harness.viewModel.submissionState {} else {
            Issue.record("expected .photoUploadFailed, got \(harness.viewModel.submissionState)")
        }
    }

    @Test("continue without photo: completes with a nil mediaId after an upload failure")
    func submitWithoutPhotoCompletes() async {
        let harness = makeHarness(withPhoto: true, uploadFails: true)

        await harness.viewModel.submit(appState: harness.appState)           // fails at photo upload
        await harness.viewModel.submitWithoutPhoto(appState: harness.appState)

        let mediaCalls = await harness.media.callCount
        let onboardingCalls = await harness.onboarding.callCount
        let sentDraft = await harness.onboarding.lastDraft
        #expect(mediaCalls == 1)                                             // not retried
        #expect(onboardingCalls == 1)
        #expect(sentDraft?.profileMediaId == nil)
        #expect(harness.viewModel.draft.profileImageFileName == nil)
        #expect(harness.appState.isOnboarded)
    }

    @Test("retry after a completion failure reuses the uploaded photo instead of re-uploading")
    func retryDoesNotReuploadPhoto() async {
        let mediaId = UUID()
        let harness = makeHarness(withPhoto: true, uploadMediaId: mediaId, completionFailFirst: 1)

        await harness.viewModel.submit(appState: harness.appState)           // upload ok, completion fails
        #expect(harness.viewModel.submissionState == .failed("제출에 실패했어요. 잠시 후 다시 시도해주세요."))
        let mediaCallsAfterFirst = await harness.media.callCount
        #expect(mediaCallsAfterFirst == 1)

        await harness.viewModel.submit(appState: harness.appState)           // retry

        let mediaCalls = await harness.media.callCount
        let onboardingCalls = await harness.onboarding.callCount
        let sentDraft = await harness.onboarding.lastDraft
        #expect(mediaCalls == 1)                                             // photo NOT re-uploaded
        #expect(onboardingCalls == 2)
        #expect(sentDraft?.profileMediaId == mediaId)
        #expect(harness.appState.isOnboarded)
    }

    @Test("one farm completes onboarding without standalone farm creation")
    func representativeOnly() async {
        let harness = makeHarness(withPhoto: false, farmCount: 1)

        await harness.viewModel.submit(appState: harness.appState)

        let onboardingCalls = await harness.onboarding.callCount
        let createdNames = await harness.farm.createdNames
        #expect(onboardingCalls == 1)
        #expect(createdNames.isEmpty)
    }

    @Test("additional farms are created after onboarding in draft order")
    func createsAdditionalFarms() async {
        let harness = makeHarness(withPhoto: false, farmCount: 3)

        await harness.viewModel.submit(appState: harness.appState)

        let onboardingCalls = await harness.onboarding.callCount
        let createdNames = await harness.farm.createdNames
        #expect(onboardingCalls == 1)
        #expect(createdNames == ["추가농장1", "추가농장2"])
        #expect(harness.appState.isOnboarded)
    }

    @Test("extra farm failure still completes onboarding and retains pending requests")
    func extraFarmFailureIsRecoverable() async {
        let harness = makeHarness(withPhoto: false, farmCount: 3, farmFailAtCall: 1)

        await harness.viewModel.submit(appState: harness.appState)

        let onboardingCalls = await harness.onboarding.callCount
        let pendingNames = await harness.pendingStore.load().map(\.name)
        #expect(onboardingCalls == 1)
        #expect(harness.appState.isOnboarded)
        #expect(pendingNames == ["추가농장1", "추가농장2"])
    }
}
