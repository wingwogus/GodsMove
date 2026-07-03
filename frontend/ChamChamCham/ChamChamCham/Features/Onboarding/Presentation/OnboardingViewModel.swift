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

    var currentStep: Step
    var draft: OnboardingDraft

    private let store: OnboardingDraftStore

    init(store: OnboardingDraftStore = OnboardingDraftStore()) {
        self.store = store
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

    func persist() {
        store.save(OnboardingDraftSnapshot(step: currentStep, draft: draft))
    }
}
