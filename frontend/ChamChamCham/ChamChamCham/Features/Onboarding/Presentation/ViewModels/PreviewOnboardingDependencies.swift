//
//  PreviewOnboardingDependencies.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

//#if DEBUG
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

struct PreviewMediaUploadRepository: MediaUploadRepository {
    func uploadProfileImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }

    func uploadCommunityImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }

    func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }
}

struct PreviewCropCatalogService: CropCatalogService {
    func fetchCrops() async throws -> [Crop] {
        [
            Crop(id: UUID(), name: "감초", categoryCode: "ROOT_BARK", categoryLabel: "뿌리·껍질"),
            Crop(id: UUID(), name: "당귀", categoryCode: "ROOT_BARK", categoryLabel: "뿌리·껍질"),
            Crop(id: UUID(), name: "작약", categoryCode: "ROOT_BARK", categoryLabel: "뿌리·껍질"),
            Crop(id: UUID(), name: "황기", categoryCode: "WHOLE_HERB", categoryLabel: "전초")
        ]
    }

    func fetchCrops(categoryCode: String) async throws -> [Crop] {
        try await fetchCrops()
    }

    func fetchCategories() async throws -> [CropCategory] {
        [
            CropCategory(code: "WHOLE_HERB", label: "전초"),
            CropCategory(code: "ROOT_BARK", label: "뿌리·껍질"),
            CropCategory(code: "RHIZOME", label: "뿌리줄기"),
            CropCategory(code: "LEAF", label: "잎"),
            CropCategory(code: "FLOWER", label: "꽃"),
            CropCategory(code: "FRUIT", label: "열매·과실"),
            CropCategory(code: "SEED", label: "종자"),
            CropCategory(code: "STEM_BRANCH", label: "줄기·가지"),
            CropCategory(code: "UNKNOWN", label: "기타")
        ]
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
            profileImageUrl: member.profileImageUrl,
            onboardingStatusRaw: onboarding.status.rawValue,
            missingFieldsRaw: onboarding.missingFields,
            updatedAt: Date()
        )
    }
}

struct PreviewFarmRepository: FarmRepository {
    func listFarms() async throws -> [StandaloneFarmResponseDTO] { [] }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        throw OnboardingSubmissionError.missingRequiredField("preview")
    }
}

extension OnboardingViewModel {
    static func preview() -> OnboardingViewModel {
        let pendingStore = PendingFarmStore(
            defaults: UserDefaults(suiteName: "preview-pending-farms")!
        )
        return OnboardingViewModel(
            onboardingRepository: PreviewOnboardingRepository(),
            mediaUploadRepository: PreviewMediaUploadRepository(),
            cropCatalogService: PreviewCropCatalogService(),
            memberProfileCache: PreviewMemberProfileCache(),
            pendingFarmSyncService: PendingFarmSyncService(
                store: pendingStore,
                repository: PreviewFarmRepository()
            )
        )
    }
}
//#endif
