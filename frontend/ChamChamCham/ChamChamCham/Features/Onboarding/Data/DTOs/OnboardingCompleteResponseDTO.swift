//
//  OnboardingCompleteResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct OnboardingCompleteResponseDTO: Decodable, Sendable {
    let member: MemberProfileResponseDTO
    let farm: FarmResponseDTO
    let crops: [CropResponseDTO]
    let onboarding: OnboardingResponseDTO
}

struct FarmResponseDTO: Decodable, Sendable {
    let id: UUID
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let latitude: Double?
    let longitude: Double?
    let pnu: String?
    let landCategory: String?
    let areaSqm: Double?
    let areaIsManualEntry: Bool
    let boundaryCoordinates: [FarmBoundaryCoordinateDTO]
    // `dataSource` is present in the real response body but intentionally not declared here —
    // JSONDecoder ignores undeclared keys, and nothing client-side consumes it.
}

struct CropResponseDTO: Decodable, Sendable {
    let id: UUID
    let externalNo: Int
    let name: String
    let usePartCategory: String
    let usePartCategoryLabel: String
}
