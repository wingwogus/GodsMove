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
    let name: String
    let phone: String
    let birthDate: String
    let nickname: String
    let experienceLevel: Int
    let managementType: String
    let farms: [FarmRequestDTO]
    let profileMediaId: UUID?
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
