//
//  LoginResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct LoginResponseDTO: Decodable, Sendable {
    let accessToken: String
    let refreshToken: String
    let member: MemberProfileResponseDTO
    let onboarding: OnboardingResponseDTO
}
