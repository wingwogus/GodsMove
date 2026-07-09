//
//  OnboardingViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Observation

@Observable
@MainActor
final class OnboardingViewModel {
    enum Step: String, CaseIterable, Codable {
        case landing
        case basicProfile
        case cropSelection
        case farmLocation
        case complete
    }

    enum SubmissionState: Equatable {
        case idle
        case submitting
        case failed(String)
    }

    var currentStep: Step
    var draft: OnboardingDraft

    var availableCrops: [Crop] = []
    var cropCategoryLabels: [String] = []
    var isLoadingCrops = false
    var cropLoadError: String?

    var submissionState: SubmissionState = .idle

    private let store: OnboardingDraftStore
    private let onboardingRepository: OnboardingRepository
    private let cropCatalogService: CropCatalogService
    private let memberProfileCache: MemberProfileCache

    init(
        store: OnboardingDraftStore = OnboardingDraftStore(),
        onboardingRepository: OnboardingRepository,
        cropCatalogService: CropCatalogService,
        memberProfileCache: MemberProfileCache
    ) {
        self.store = store
        self.onboardingRepository = onboardingRepository
        self.cropCatalogService = cropCatalogService
        self.memberProfileCache = memberProfileCache
        if let snapshot = store.load() {
            self.currentStep = snapshot.step
            self.draft = snapshot.draft
        } else {
            self.currentStep = .landing
            self.draft = OnboardingDraft()
        }
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
            async let categories = cropCatalogService.fetchCategoryLabels()
            availableCrops = try await crops
            // "인기" (popular) is a purely client-side "show everything" affordance — the backend has no such category.
            cropCategoryLabels = ["인기"] + (try await categories)
        } catch {
            cropLoadError = "작물 목록을 불러오지 못했어요. 다시 시도해주세요."
        }
    }

    func submit(appState: AppState) async {
        guard submissionState != .submitting else { return }
        submissionState = .submitting
        do {
            let response = try await onboardingRepository.completeOnboarding(draft)
            memberProfileCache.save(member: response.member, onboarding: response.onboarding)
            store.clear()
            appState.isOnboarded = true
            submissionState = .idle
        } catch {
            submissionState = .failed("제출에 실패했어요. 잠시 후 다시 시도해주세요.")
        }
    }
}
