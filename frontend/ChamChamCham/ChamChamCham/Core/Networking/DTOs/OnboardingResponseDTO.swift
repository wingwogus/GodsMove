//
//  OnboardingResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import Foundation

/// Shared across `Features/Auth` (login response) and `Features/Onboarding` (onboarding-complete response) —
/// lives in Core, not either feature, because `MemberProfileCache` (Core/Persistence) also depends on it directly.
enum OnboardingStatusDTO: String, Decodable, Sendable {
    case required = "REQUIRED"
    case complete = "COMPLETE"
}

struct OnboardingResponseDTO: Decodable, Sendable {
    let status: OnboardingStatusDTO
    let missingFields: [String]
}
