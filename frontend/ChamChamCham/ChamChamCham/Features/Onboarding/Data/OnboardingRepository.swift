//
//  OnboardingRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

protocol OnboardingRepository: Sendable {
    func completeOnboarding(_ draft: OnboardingDraft) async throws -> OnboardingCompleteResponseDTO
}

struct RemoteOnboardingRepository: OnboardingRepository {
    let apiClient: APIClient

    func completeOnboarding(_ draft: OnboardingDraft) async throws -> OnboardingCompleteResponseDTO {
        let requestDTO = try OnboardingCompleteRequestDTO(draft: draft)
        return try await apiClient.send(OnboardingEndpoint.completeOnboarding(requestDTO))
    }
}
