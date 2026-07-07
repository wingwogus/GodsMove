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
    let farm: FarmRequestDTO
    let cropIds: [UUID]
    // Optional server-side (`profileMediaId: UUID? = null`) — the profile photo is a "선택" field, so onboarding
    // completes with or without it. Nil when the user skipped the photo or its upload failed and they chose to proceed.
    let profileMediaId: UUID?

    init(draft: OnboardingDraft) throws {
        guard !draft.name.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("name")
        }
        guard !draft.phone.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("phone")
        }
        guard let birthDate = draft.birthDate else {
            throw OnboardingSubmissionError.missingRequiredField("birthDate")
        }
        guard let experienceYears = draft.experienceYears else {
            throw OnboardingSubmissionError.missingRequiredField("experienceYears")
        }
        guard let managementType = draft.managementType else {
            throw OnboardingSubmissionError.missingRequiredField("managementType")
        }
        guard !draft.cropIDs.isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("cropIDs")
        }

        self.name = draft.name
        self.phone = draft.phone
        self.birthDate = Self.wireDateFormatter.string(from: birthDate)
        self.nickname = draft.nickname
        self.experienceLevel = experienceYears
        self.managementType = managementType.rawValue
        self.farm = try FarmRequestDTO(draft: draft)
        self.cropIds = draft.cropIDs
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

struct FarmRequestDTO: Encodable, Sendable {
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let latitude: Double
    let longitude: Double
    let pnu: String?
    let landCategory: String?
    let areaSqm: Double?
    let areaIsManualEntry: Bool
    // Parcel polygon is intentionally not persisted in the draft (screen-local map state only), so this is always [].
    let boundaryCoordinates: [FarmBoundaryCoordinateDTO]
    // `dataSource` intentionally omitted — backend's FarmRequest field is optional with a Kotlin default.

    init(draft: OnboardingDraft) throws {
        guard !draft.farmName.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("farmName")
        }
        guard !draft.farmRoadAddress.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("farmRoadAddress")
        }
        guard let latitude = draft.farmLatitude, let longitude = draft.farmLongitude else {
            throw OnboardingSubmissionError.missingRequiredField("farmCoordinate")
        }

        self.name = draft.farmName
        self.roadAddress = draft.farmRoadAddress
        self.jibunAddress = draft.farmJibunAddress.isEmpty ? nil : draft.farmJibunAddress
        self.latitude = latitude
        self.longitude = longitude
        self.pnu = draft.farmPNU
        self.landCategory = draft.farmLandCategory
        self.areaSqm = draft.farmAreaSqm
        self.areaIsManualEntry = draft.farmAreaIsManualEntry
        self.boundaryCoordinates = []
    }
}

struct FarmBoundaryCoordinateDTO: Codable, Sendable {
    let latitude: Double
    let longitude: Double
}
