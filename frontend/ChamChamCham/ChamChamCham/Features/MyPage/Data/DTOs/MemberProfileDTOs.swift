//
//  MemberProfileDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

struct MyProfileResponseDTO: Decodable, Sendable {
    let memberId: UUID
    let email: String?
    let name: String?
    let phone: String?
    let birthDate: String?
    let nickname: String?
    let experienceLevel: Int?
    let managementType: String?
    let profileImageUrl: String?
    let farms: [MyFarmResponseDTO]
    let crops: [CropProfileResponseDTO]
}

struct PublicProfileResponseDTO: Decodable, Sendable {
    let memberId: UUID
    let nickname: String?
    let experienceLevel: Int?
    let managementType: String?
    let profileImageUrl: String?
    let farms: [PublicFarmResponseDTO]
    let crops: [CropProfileResponseDTO]
}

struct MyFarmResponseDTO: Decodable, Sendable {
    let farmId: UUID
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let displayRegion: String?
}

struct PublicFarmResponseDTO: Decodable, Sendable {
    let farmId: UUID
    let displayRegion: String?
}

struct CropProfileResponseDTO: Decodable, Sendable {
    let cropId: UUID
    let cropName: String
}

struct UpdateMyProfileRequestDTO: Encodable, Sendable {
    // name/phone/birthDate/nickname are all optional server-side (Guideline 5.1.1(v) — not directly relevant to
    // the app's core functionality, so profile edits must not require them).
    let name: String
    let phone: String
    let birthDate: String?
    let nickname: String
    let experienceLevel: Int
    let managementType: String
    let profileMediaId: UUID?
    let farms: [UpdateMyProfileFarmRequestDTO]
    /// The server can't compute this itself since `birthDate` may be absent — the client sends its own
    /// birthDate-vs-experienceLevel check, mirroring `OnboardingDraft.isExperienceLevelWithinAge`.
    let isExperienceLevelWithinAge: Bool
}

/// Backend's `PUT /members/me/profile` treats the whole profile as one atomic payload and requires at least one
/// farm (`farms: NotEmpty`). Each entry is matched to an existing farm by `farmId` and upserted with the same
/// values it already has — see `StandaloneFarmResponseDTO.toUpdateMyProfileFarmRequest()`.
struct UpdateMyProfileFarmRequestDTO: Encodable, Sendable {
    let farmId: UUID?
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let latitude: Double
    let longitude: Double
    let pnu: String?
    let landCategory: String?
    let areaSqm: Double?
    let areaIsManualEntry: Bool
    let boundaryCoordinates: [FarmBoundaryCoordinateDTO]
    let dataSource: FarmDataSourceDTO
    let cropIds: [UUID]
}

extension MyProfileResponseDTO {
    func toDomain() -> MyMemberProfile {
        MyMemberProfile(
            memberId: memberId,
            email: email,
            name: name,
            phone: phone,
            birthDate: birthDate,
            nickname: nickname,
            experienceLevel: experienceLevel,
            managementType: managementType,
            profileImageUrl: profileImageUrl,
            farms: farms.map { $0.toDomain() },
            crops: crops.map { $0.toDomain() }
        )
    }
}

extension PublicProfileResponseDTO {
    func toDomain() -> PublicMemberProfile {
        PublicMemberProfile(
            memberId: memberId,
            nickname: nickname,
            experienceLevel: experienceLevel,
            managementType: managementType,
            profileImageUrl: profileImageUrl,
            farms: farms.map { $0.toDomain() },
            crops: crops.map { $0.toDomain() }
        )
    }
}

extension MyFarmResponseDTO {
    func toDomain() -> MyMemberFarm {
        MyMemberFarm(
            farmId: farmId,
            name: name,
            roadAddress: roadAddress,
            jibunAddress: jibunAddress,
            displayRegion: displayRegion
        )
    }
}

extension PublicFarmResponseDTO {
    func toDomain() -> PublicMemberFarm {
        PublicMemberFarm(farmId: farmId, displayRegion: displayRegion)
    }
}

extension CropProfileResponseDTO {
    func toDomain() -> MemberCropProfile {
        MemberCropProfile(cropId: cropId, cropName: cropName)
    }
}

extension StandaloneFarmResponseDTO {
    /// Maps this farm back into the shape `PUT /members/me/profile` requires for its `farms` entries.
    /// Farms are always created with coordinates and at least one crop (onboarding/farm-add both require both),
    /// so `nil` here would mean corrupt upstream data rather than a normal state — returning `nil` lets the
    /// caller drop it rather than send a request the backend is guaranteed to reject.
    func toUpdateMyProfileFarmRequest() -> UpdateMyProfileFarmRequestDTO? {
        guard let latitude, let longitude else { return nil }
        return UpdateMyProfileFarmRequestDTO(
            farmId: farmId,
            name: name,
            roadAddress: roadAddress,
            jibunAddress: jibunAddress,
            latitude: latitude,
            longitude: longitude,
            pnu: pnu,
            landCategory: landCategory,
            areaSqm: areaSqm,
            areaIsManualEntry: areaIsManualEntry,
            boundaryCoordinates: boundaryCoordinates,
            dataSource: dataSource,
            cropIds: crops.map(\.id)
        )
    }
}
