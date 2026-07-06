//
//  PreviewOnboardingDependencies.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

#if DEBUG
/// SwiftUI preview-only stand-ins — `OnboardingViewModel` takes its repositories as required init parameters
/// (no default) so production call sites can never silently fall back to fake data; previews use these instead.
struct PreviewOnboardingRepository: OnboardingRepository {
    func completeOnboarding(_ draft: OnboardingDraft) async throws -> OnboardingCompleteResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }
}

struct PreviewAuthRepository: AuthRepository {
    func loginWithKakao(idToken: String, nonce: String, kakaoAccessToken: String?) async throws -> LoginResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }

    func loginWithApple(
        identityToken: String,
        nonce: String,
        authorizationCode: String?,
        userIdentifier: String?
    ) async throws -> LoginResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }

    func loginWithNaver(accessToken: String) async throws -> LoginResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }

    func logout() async throws {}
}

struct PreviewCropCatalogService: CropCatalogService {
    func fetchCrops() async throws -> [Crop] {
        [
            Crop(id: UUID(), name: "황기", category: "약초류"),
            Crop(id: UUID(), name: "당귀", category: "약초류"),
            Crop(id: UUID(), name: "작약", category: "약초류")
        ]
    }

    func fetchCategoryLabels() async throws -> [String] {
        ["약초류", "근채류", "화훼·열매", "허브·잎"]
    }
}

@MainActor
struct PreviewMemberProfileCache: MemberProfileCache {
    func save(member: MemberProfileResponseDTO, onboarding: OnboardingResponseDTO) -> CachedMemberProfile {
        CachedMemberProfile(
            id: member.id,
            email: member.email,
            name: member.name,
            nickname: member.nickname,
            phone: member.phone,
            birthDateRaw: member.birthDate,
            experienceLevel: member.experienceLevel,
            managementTypeRaw: member.managementType,
            onboardingStatusRaw: onboarding.status.rawValue,
            missingFieldsRaw: onboarding.missingFields,
            updatedAt: Date()
        )
    }
}

extension OnboardingViewModel {
    static func preview() -> OnboardingViewModel {
        OnboardingViewModel(
            onboardingRepository: PreviewOnboardingRepository(),
            cropCatalogService: PreviewCropCatalogService(),
            memberProfileCache: PreviewMemberProfileCache()
        )
    }
}
#endif
