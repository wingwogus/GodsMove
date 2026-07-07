//
//  MemberProfileResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct MemberProfileResponseDTO: Decodable, Sendable {
    let id: UUID
    let email: String?
    let name: String?
    let phone: String?
    let birthDate: String?
    let nickname: String?
    let experienceLevel: Int?
    let managementType: String?
    let profileImageUrl: String?
}
