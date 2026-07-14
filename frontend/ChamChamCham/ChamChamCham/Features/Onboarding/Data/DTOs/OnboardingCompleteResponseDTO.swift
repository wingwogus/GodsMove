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

struct CropResponseDTO: Decodable, Sendable {
    let id: UUID
    let externalNo: Int
    let name: String
    let usePartCategory: String
    let usePartCategoryLabel: String
}
