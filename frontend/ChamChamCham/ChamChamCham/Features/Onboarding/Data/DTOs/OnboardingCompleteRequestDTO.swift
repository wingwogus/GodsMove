//
//  OnboardingCompleteRequestDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

/// Thrown by `OnboardingCompleteRequestDTO.init(draft:)` if a field the per-step UI validation should have already
/// guaranteed is somehow still missing — defensive only, force-unwrapping here would be a real crash risk otherwise.
enum OnboardingSubmissionError: Error, Sendable {
    case missingRequiredField(String)
}

struct OnboardingCompleteRequestDTO: Encodable, Sendable {
    let name: String
    let phone: String
    let birthDate: String
    let nickname: String
    let experienceLevel: Int
    let managementType: String
    let farm: FarmDraftRequestDTO
    let cropIds: [UUID]
    // Optional server-side (`profileMediaId: UUID? = null`) — the profile photo is a "선택" field, so onboarding
    // completes with or without it. Nil when the user skipped the photo or its upload failed and they chose to proceed.
    let profileMediaId: UUID?

    init(draft: OnboardingDraft) throws {
        let representativeFarm = draft.representativeFarm

        guard !draft.name.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("name")
        }
        guard !draft.phone.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("phone")
        }
        guard let birthDate = draft.birthDate else {
            throw OnboardingSubmissionError.missingRequiredField("birthDate")
        }
        guard !draft.nickname.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("nickname")
        }
        guard let experienceYears = draft.experienceYears else {
            throw OnboardingSubmissionError.missingRequiredField("experienceYears")
        }
        guard let managementType = draft.managementType else {
            throw OnboardingSubmissionError.missingRequiredField("managementType")
        }
        guard !representativeFarm.cropIDs.isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("cropIDs")
        }
        guard representativeFarm.cropIDs.count <= 5 else {
            throw OnboardingSubmissionError.missingRequiredField("cropIDs")
        }

        self.name = draft.name
        self.phone = draft.phone
        self.birthDate = Self.wireDateFormatter.string(from: birthDate)
        self.nickname = draft.nickname
        self.experienceLevel = experienceYears
        self.managementType = managementType.rawValue
        self.farm = try FarmDraftRequestDTO(farm: representativeFarm)
        self.cropIds = representativeFarm.cropIDs
        self.profileMediaId = draft.profileMediaId
    }

    private static let wireDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

extension FarmDraftRequestDTO {
    init(draft: OnboardingDraft) throws {
        try self.init(farm: draft.representativeFarm)
    }

    init(farm: OnboardingFarmDraft) throws {
        guard !farm.farmName.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("farmName")
        }
        guard !farm.farmRoadAddress.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("farmRoadAddress")
        }
        guard let latitude = farm.farmLatitude, let longitude = farm.farmLongitude else {
            throw OnboardingSubmissionError.missingRequiredField("farmCoordinate")
        }
        if let areaSqm = farm.farmAreaSqm, areaSqm <= 0 {
            throw OnboardingSubmissionError.missingRequiredField("farmAreaSqm")
        }

        self.name = farm.farmName
        self.roadAddress = farm.farmRoadAddress
        self.jibunAddress = farm.farmJibunAddress.isEmpty ? nil : farm.farmJibunAddress
        self.latitude = latitude
        self.longitude = longitude
        self.pnu = farm.farmPNU
        self.landCategory = farm.farmLandCategory
        self.areaSqm = farm.farmAreaSqm
        self.areaIsManualEntry = farm.farmAreaIsManualEntry
        self.boundaryCoordinates = []
        self.dataSource = .onboardingJusoVWorld
    }
}

extension SaveFarmRequestDTO {
    init(farm: OnboardingFarmDraft) throws {
        guard !farm.cropIDs.isEmpty,
              farm.cropIDs.count <= 5,
              Set(farm.cropIDs).count == farm.cropIDs.count else {
            throw OnboardingSubmissionError.missingRequiredField("cropIDs")
        }

        let draft = try FarmDraftRequestDTO(farm: farm)
        self.name = draft.name
        self.roadAddress = draft.roadAddress
        self.jibunAddress = draft.jibunAddress
        self.latitude = draft.latitude
        self.longitude = draft.longitude
        self.pnu = draft.pnu
        self.landCategory = draft.landCategory
        self.areaSqm = draft.areaSqm
        self.areaIsManualEntry = draft.areaIsManualEntry
        self.boundaryCoordinates = draft.boundaryCoordinates
        self.dataSource = draft.dataSource
        self.cropIds = farm.cropIDs
    }
}
