//
//  MemberProfile.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

struct MyMemberProfile: Sendable, Equatable {
    let memberId: UUID
    let email: String?
    let name: String?
    let phone: String?
    let birthDate: String?
    let nickname: String?
    let experienceLevel: Int?
    let managementType: String?
    let profileImageUrl: String?
    let farms: [MyMemberFarm]
    let crops: [MemberCropProfile]
}

struct PublicMemberProfile: Sendable, Equatable {
    let memberId: UUID
    let nickname: String?
    let experienceLevel: Int?
    let managementType: String?
    let profileImageUrl: String?
    let farms: [PublicMemberFarm]
    let crops: [MemberCropProfile]
}

struct MyMemberFarm: Sendable, Equatable {
    let farmId: UUID
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let displayRegion: String?
}

struct PublicMemberFarm: Sendable, Equatable {
    let farmId: UUID
    let displayRegion: String?
}

struct MemberCropProfile: Sendable, Equatable {
    let cropId: UUID
    let cropName: String
}
