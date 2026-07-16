//
//  OnboardingViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation
import Observation

@Observable
@MainActor
final class OnboardingViewModel {
    enum Step: String, CaseIterable, Codable {
        case landing
        case basicProfile
        case farmLocation
        case cropSelection
        case complete
    }

    enum SubmissionState: Equatable {
        case idle
        case uploadingPhoto
        case submitting
        /// Onboarding completion failed — only a retry makes sense.
        case failed(String)
        /// The profile photo (a "선택" field) failed to upload. The user can retry or proceed without it.
        case photoUploadFailed(String)
    }

    enum CropSelectionToggleResult: Equatable {
        case selected
        case deselected
        case selectionLimitReached
    }

    enum BasicProfileField: Hashable {
        case name
        case nickname
        case phone
        case birthDate
        case experienceYears
        case managementType
    }

    var currentStep: Step
    var draft: OnboardingDraft

    var availableCrops: [Crop] = []
    var cropCategories: [CropCategory] = []
    var isLoadingCrops = false
    var cropLoadError: String?

    var submissionState: SubmissionState = .idle

    var shouldShowResumePrompt: Bool { pendingResumeSnapshot != nil }
    var canProceedFromBasicProfile: Bool { basicProfileValidationErrors.isEmpty }

    var basicProfileValidationErrors: [BasicProfileField: String] {
        var errors: [BasicProfileField: String] = [:]
        if draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errors[.name] = "이름은 필수로 입력해주세요."
        }
        if draft.nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errors[.nickname] = "닉네임은 필수로 입력해주세요."
        }
        if draft.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errors[.phone] = "연락처는 필수로 입력해주세요."
        }
        if draft.birthDate == nil {
            errors[.birthDate] = "생년월일은 필수로 입력해주세요."
        }
        if draft.experienceYears == nil {
            errors[.experienceYears] = "귀농 년차는 필수로 입력해주세요."
        }
        if draft.managementType == nil {
            errors[.managementType] = "자격은 필수로 선택해주세요."
        }
        return errors
    }

    private var pendingResumeSnapshot: OnboardingDraftSnapshot?
    private let store: OnboardingDraftStore
    private let onboardingRepository: OnboardingRepository
    private let mediaUploadRepository: MediaUploadRepository
    private let cropCatalogService: CropCatalogService
    private let memberProfileCache: MemberProfileCache
    private let pendingFarmSyncService: PendingFarmSyncService

    init(
        store: OnboardingDraftStore = OnboardingDraftStore(),
        onboardingRepository: OnboardingRepository,
        mediaUploadRepository: MediaUploadRepository,
        cropCatalogService: CropCatalogService,
        memberProfileCache: MemberProfileCache,
        pendingFarmSyncService: PendingFarmSyncService
    ) {
        self.store = store
        self.onboardingRepository = onboardingRepository
        self.mediaUploadRepository = mediaUploadRepository
        self.cropCatalogService = cropCatalogService
        self.memberProfileCache = memberProfileCache
        self.pendingFarmSyncService = pendingFarmSyncService
        if let snapshot = store.load(), snapshot.isRestorable {
            self.pendingResumeSnapshot = snapshot
            self.currentStep = .landing
            self.draft = OnboardingDraft()
        } else {
            if store.load() != nil {
                store.clear()
            }
            self.currentStep = .landing
            self.draft = Self.seededDraft(from: memberProfileCache)
        }
    }

    /// Seeds a genuinely new draft with whatever the social login provider already gave the backend. Never called
    /// for a restorable snapshot, so this can't clobber an in-progress draft for the same member.
    private static func seededDraft(from cache: MemberProfileCache) -> OnboardingDraft {
        guard let member = cache.fetchCurrent() else { return OnboardingDraft() }
        return OnboardingDraft(prefillingFrom: member)
    }

    func goNext() {
        guard let index = Step.allCases.firstIndex(of: currentStep),
              index + 1 < Step.allCases.count else { return }
        currentStep = Step.allCases[index + 1]
        persist()
    }

    func goBack() {
        guard let index = Step.allCases.firstIndex(of: currentStep),
              index > 0 else { return }
        currentStep = Step.allCases[index - 1]
        persist()
    }

    /// Used when a provider login succeeds but onboarding is still `.required` — skips straight past `.landing`.
    func jump(to step: Step) {
        currentStep = step
        persist()
    }

    func continueAfterAuthentication() {
        guard !shouldShowResumePrompt else { return }
        jump(to: .basicProfile)
    }

    func resumeSavedDraft() {
        guard let snapshot = pendingResumeSnapshot else { return }
        pendingResumeSnapshot = nil
        currentStep = snapshot.step
        draft = snapshot.draft
        persist()
    }

    func discardSavedDraftAndStartOver() {
        pendingResumeSnapshot = nil
        store.clear()
        submissionState = .idle
        currentStep = .basicProfile
        draft = Self.seededDraft(from: memberProfileCache)
    }

    func addFarmFromCompletion() {
        draft.addEmptyFarmAndSelect()
        submissionState = .idle
        currentStep = .farmLocation
        persist()
    }

    @discardableResult
    func toggleCropSelection(_ cropID: UUID) -> CropSelectionToggleResult {
        if let index = draft.cropIDs.firstIndex(of: cropID) {
            draft.cropIDs.remove(at: index)
            persist()
            return .deselected
        }

        guard draft.cropIDs.count < 5 else {
            return .selectionLimitReached
        }

        draft.cropIDs.append(cropID)
        persist()
        return .selected
    }

    func persist() {
        store.save(OnboardingDraftSnapshot(step: currentStep, draft: draft))
    }

    func loadCropsIfNeeded() async {
        guard availableCrops.isEmpty, !isLoadingCrops else { return }
        isLoadingCrops = true
        cropLoadError = nil
        defer { isLoadingCrops = false }
        do {
            async let crops = cropCatalogService.fetchCrops()
            async let categories = cropCatalogService.fetchCategories()
            availableCrops = try await crops
            cropCategories = try await categories
        } catch {
            cropLoadError = "작물 목록을 불러오지 못했어요. 다시 시도해주세요."
        }
    }

    /// Runs the two-phase submission: upload the profile photo (if any, and not already uploaded), then complete
    /// onboarding. Splitting the phases lets a photo-upload failure — which is recoverable, since the photo is
    /// optional — surface differently from a completion failure, and lets a completion retry skip a re-upload.
    func submit(appState: AppState) async {
        guard submissionState != .submitting, submissionState != .uploadingPhoto else { return }

        if let failure = await uploadProfilePhotoIfNeeded() {
            submissionState = .photoUploadFailed(failure)
            return
        }

        await complete(appState: appState)
    }

    /// Drops the pending photo and submits without it. Used when the photo upload failed and the user chose to
    /// continue — the photo is a "선택" field, so onboarding must not be blocked on it.
    func submitWithoutPhoto(appState: AppState) async {
        draft.profileImageFileName = nil
        draft.profileMediaId = nil
        persist()
        await complete(appState: appState)
    }

    /// Returns a user-facing error string on failure, or `nil` when there's nothing to upload or the upload succeeded
    /// (with `draft.profileMediaId` now set and persisted).
    private func uploadProfilePhotoIfNeeded() async -> String? {
        guard draft.profileMediaId == nil, let fileName = draft.profileImageFileName else { return nil }
        // The snapshot's filename outlived its file (e.g. cache cleared) — nothing to upload, proceed without a photo.
        guard let imageData = store.loadProfileImage(fileName: fileName) else { return nil }

        submissionState = .uploadingPhoto
        do {
            let uploaded = try await mediaUploadRepository.uploadProfileImage(imageData, originalFilename: fileName)
            draft.profileMediaId = uploaded.mediaId
            persist()
            return nil
        } catch {
            return "프로필 사진을 올리지 못했어요. 사진 없이 계속하거나 다시 시도할 수 있어요."
        }
    }

    private func complete(appState: AppState) async {
        submissionState = .submitting
        do {
            let extraFarmRequests = try draft.farms.dropFirst().map(SaveFarmRequestDTO.init(farm:))
            let response = try await onboardingRepository.completeOnboarding(draft)
            memberProfileCache.save(member: response.member, onboarding: response.onboarding)
            await pendingFarmSyncService.enqueue(extraFarmRequests, memberId: response.member.id)
            store.clear()
            appState.isOnboarded = true
            submissionState = .idle
            await pendingFarmSyncService.syncPending(memberId: response.member.id)
        } catch {
            submissionState = .failed("제출에 실패했어요. 잠시 후 다시 시도해주세요.")
        }
    }
}

private extension OnboardingDraftSnapshot {
    var isRestorable: Bool {
        step != .landing && draft.hasRecoverableInput
    }
}

private extension OnboardingDraft {
    var hasRecoverableInput: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || birthDate != nil
            || experienceYears != nil
            || profileImageFileName != nil
            || profileMediaId != nil
            || farms.contains { $0.hasRecoverableInput }
    }
}

private extension OnboardingFarmDraft {
    var hasRecoverableInput: Bool {
        !cropIDs.isEmpty
            || !farmName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !farmRoadAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !farmJibunAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || farmLatitude != nil
            || farmLongitude != nil
            || farmPNU != nil
            || farmLandCategory != nil
            || farmAreaSqm != nil
    }
}
