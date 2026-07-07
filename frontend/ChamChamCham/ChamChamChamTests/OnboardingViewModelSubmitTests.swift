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
        let appState: AppState
    }

    /// Builds a view model over an isolated store. When `withPhoto` is true, a local photo file is saved and its
    /// name written onto the draft, so the submit flow has something to upload.
    private func makeHarness(
        withPhoto: Bool,
        uploadMediaId: UUID = UUID(),
        uploadFails: Bool = false,
        completionFailFirst: Int = 0
    ) -> Harness {
        let store = OnboardingTestFactory.isolatedStore()
        var draft = OnboardingTestFactory.validDraft()
        if withPhoto {
            draft.profileImageFileName = store.saveProfileImage(Data("fake-image".utf8))
        }
        store.save(OnboardingDraftSnapshot(step: .complete, draft: draft))

        let media = FakeMediaUploadRepository(successMediaId: uploadMediaId, fails: uploadFails)
        let onboarding = FakeOnboardingRepository(failFirst: completionFailFirst)
        let viewModel = OnboardingViewModel(
            store: store,
            onboardingRepository: onboarding,
            mediaUploadRepository: media,
            cropCatalogService: StubCropCatalogService(),
            memberProfileCache: StubMemberProfileCache()
        )
        return Harness(viewModel: viewModel, store: store, media: media, onboarding: onboarding, appState: AppState())
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
}
