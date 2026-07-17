//
//  OnboardingDraft.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct OnboardingDraft: Codable {
    var name: String = ""
    var nickname: String = ""
    var phone: String = ""
    var birthDate: Date?
    var experienceYears: Int?
    var managementType: ManagementType? = .agriculturalIndividual
    var profileImageFileName: String?
    /// Set once the local profile photo has been uploaded to the media service. Persisted in the draft snapshot so
    /// a failed onboarding-complete followed by "다시 시도" reuses the already-uploaded media instead of re-uploading.
    var profileMediaId: UUID?
    var farms: [OnboardingFarmDraft] = [OnboardingFarmDraft()]
    var activeFarmIndex: Int = 0

    var activeFarm: OnboardingFarmDraft {
        get {
            guard !farms.isEmpty else { return OnboardingFarmDraft() }
            return farms[clampedActiveFarmIndex]
        }
        set {
            if farms.isEmpty {
                farms = [newValue]
                activeFarmIndex = 0
                return
            }
            let index = clampedActiveFarmIndex
            farms[index] = newValue
            activeFarmIndex = index
        }
    }

    var representativeFarm: OnboardingFarmDraft {
        farms.first ?? OnboardingFarmDraft()
    }

    var cropIDs: [UUID] {
        get { activeFarm.cropIDs }
        set {
            var farm = activeFarm
            farm.cropIDs = newValue
            activeFarm = farm
        }
    }

    var farmName: String {
        get { activeFarm.farmName }
        set {
            var farm = activeFarm
            farm.farmName = newValue
            activeFarm = farm
        }
    }

    var farmRoadAddress: String {
        get { activeFarm.farmRoadAddress }
        set {
            var farm = activeFarm
            farm.farmRoadAddress = newValue
            activeFarm = farm
        }
    }

    var farmJibunAddress: String {
        get { activeFarm.farmJibunAddress }
        set {
            var farm = activeFarm
            farm.farmJibunAddress = newValue
            activeFarm = farm
        }
    }

    var farmLatitude: Double? {
        get { activeFarm.farmLatitude }
        set {
            var farm = activeFarm
            farm.farmLatitude = newValue
            activeFarm = farm
        }
    }

    var farmLongitude: Double? {
        get { activeFarm.farmLongitude }
        set {
            var farm = activeFarm
            farm.farmLongitude = newValue
            activeFarm = farm
        }
    }

    var farmPNU: String? {
        get { activeFarm.farmPNU }
        set {
            var farm = activeFarm
            farm.farmPNU = newValue
            activeFarm = farm
        }
    }

    var farmLandCategory: String? {
        get { activeFarm.farmLandCategory }
        set {
            var farm = activeFarm
            farm.farmLandCategory = newValue
            activeFarm = farm
        }
    }

    var farmAreaSqm: Double? {
        get { activeFarm.farmAreaSqm }
        set {
            var farm = activeFarm
            farm.farmAreaSqm = newValue
            activeFarm = farm
        }
    }

    var farmAreaIsManualEntry: Bool {
        get { activeFarm.farmAreaIsManualEntry }
        set {
            var farm = activeFarm
            farm.farmAreaIsManualEntry = newValue
            activeFarm = farm
        }
    }

    /// 필지 경계(지적도 또는 사용자 작도). 온보딩 완료 시 그대로 `boundaryCoordinates`로 전송된다.
    var farmBoundaryCoordinates: [FarmBoundaryCoordinateDTO] {
        get { activeFarm.farmBoundaryCoordinates }
        set {
            var farm = activeFarm
            farm.farmBoundaryCoordinates = newValue
            activeFarm = farm
        }
    }

    mutating func addEmptyFarmAndSelect() {
        farms.append(OnboardingFarmDraft())
        activeFarmIndex = farms.count - 1
    }

    mutating func selectFarm(at index: Int) {
        guard farms.indices.contains(index) else { return }
        activeFarmIndex = index
    }

    private var clampedActiveFarmIndex: Int {
        guard !farms.isEmpty else { return 0 }
        return min(max(activeFarmIndex, 0), farms.count - 1)
    }

    enum CodingKeys: String, CodingKey {
        case name
        case nickname
        case phone
        case birthDate
        case experienceYears
        case managementType
        case profileImageFileName
        case profileMediaId
        case farms
        case activeFarmIndex

        // Legacy single-farm snapshot keys. Kept so existing onboarding drafts survive the Figma flow migration.
        case cropIDs
        case farmName
        case farmRoadAddress
        case farmJibunAddress
        case farmLatitude
        case farmLongitude
        case farmPNU
        case farmLandCategory
        case farmAreaSqm
        case farmAreaIsManualEntry
    }

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        name = try container.decodeIfPresent(String.self, forKey: .name) ?? ""
        nickname = try container.decodeIfPresent(String.self, forKey: .nickname) ?? ""
        phone = try container.decodeIfPresent(String.self, forKey: .phone) ?? ""
        birthDate = try container.decodeIfPresent(Date.self, forKey: .birthDate)
        experienceYears = try container.decodeIfPresent(Int.self, forKey: .experienceYears)
        managementType = try container.decodeIfPresent(ManagementType.self, forKey: .managementType) ?? .agriculturalIndividual
        profileImageFileName = try container.decodeIfPresent(String.self, forKey: .profileImageFileName)
        profileMediaId = try container.decodeIfPresent(UUID.self, forKey: .profileMediaId)

        if let decodedFarms = try container.decodeIfPresent([OnboardingFarmDraft].self, forKey: .farms),
           !decodedFarms.isEmpty {
            farms = decodedFarms
            activeFarmIndex = try container.decodeIfPresent(Int.self, forKey: .activeFarmIndex) ?? 0
            activeFarmIndex = clampedActiveFarmIndex
        } else {
            farms = [
                OnboardingFarmDraft(
                    cropIDs: try container.decodeIfPresent([UUID].self, forKey: .cropIDs) ?? [],
                    farmName: try container.decodeIfPresent(String.self, forKey: .farmName) ?? "",
                    farmRoadAddress: try container.decodeIfPresent(String.self, forKey: .farmRoadAddress) ?? "",
                    farmJibunAddress: try container.decodeIfPresent(String.self, forKey: .farmJibunAddress) ?? "",
                    farmLatitude: try container.decodeIfPresent(Double.self, forKey: .farmLatitude),
                    farmLongitude: try container.decodeIfPresent(Double.self, forKey: .farmLongitude),
                    farmPNU: try container.decodeIfPresent(String.self, forKey: .farmPNU),
                    farmLandCategory: try container.decodeIfPresent(String.self, forKey: .farmLandCategory),
                    farmAreaSqm: try container.decodeIfPresent(Double.self, forKey: .farmAreaSqm),
                    farmAreaIsManualEntry: try container.decodeIfPresent(Bool.self, forKey: .farmAreaIsManualEntry) ?? false
                )
            ]
            activeFarmIndex = 0
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(nickname, forKey: .nickname)
        try container.encode(phone, forKey: .phone)
        try container.encodeIfPresent(birthDate, forKey: .birthDate)
        try container.encodeIfPresent(experienceYears, forKey: .experienceYears)
        try container.encodeIfPresent(managementType, forKey: .managementType)
        try container.encodeIfPresent(profileImageFileName, forKey: .profileImageFileName)
        try container.encodeIfPresent(profileMediaId, forKey: .profileMediaId)
        try container.encode(farms.isEmpty ? [OnboardingFarmDraft()] : farms, forKey: .farms)
        try container.encode(clampedActiveFarmIndex, forKey: .activeFarmIndex)
    }
}

struct OnboardingFarmDraft: Codable, Equatable, Sendable {
    var cropIDs: [UUID] = []
    var farmName: String = ""
    var farmRoadAddress: String = ""
    var farmJibunAddress: String = ""
    var farmLatitude: Double?
    var farmLongitude: Double?
    var farmPNU: String?
    var farmLandCategory: String?
    var farmAreaSqm: Double?
    var farmAreaIsManualEntry: Bool = false
    var farmBoundaryCoordinates: [FarmBoundaryCoordinateDTO] = []
}

/// Raw values match the backend's `ManagementType` enum names exactly (`AuthRequests.CompleteOnboardingRequest.managementType`) —
/// `rawValue` is the entire wire-format mapping, so there's one source of truth instead of a separate translation table.
enum ManagementType: String, CaseIterable, Codable, Sendable {
    case agriculturalIndividual = "AGRICULTURAL_INDIVIDUAL"
    case agriculturalCorporation = "AGRICULTURAL_CORPORATION"
    case nonRegisteredFarmer = "NON_REGISTERED_FARMER"
}

extension OnboardingDraft {
    /// Seeds a brand-new draft with whatever the social login provider already gave the backend, per
    /// `docs/figma/onboarding/2026-07-11-onboarding-step-1-social-prefill-default.md`. Only name/phone/nickname/
    /// birthDate are prefilled — qualification and profile photo are intentionally left untouched.
    init(prefillingFrom member: CachedMemberProfile) {
        self.init()
        if let name = member.name, !name.isEmpty { self.name = name }
        if let phone = member.phone, !phone.isEmpty { self.phone = phone }
        if let nickname = member.nickname, !nickname.isEmpty { self.nickname = nickname }
        if let raw = member.birthDateRaw {
            self.birthDate = OnboardingCompleteRequestDTO.wireDateFormatter.date(from: raw)
        }
    }
}
