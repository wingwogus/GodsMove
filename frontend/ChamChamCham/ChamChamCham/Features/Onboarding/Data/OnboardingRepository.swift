//
//  OnboardingRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

protocol OnboardingRepository {
    func completeOnboarding(_ draft: OnboardingDraft) async throws
}
