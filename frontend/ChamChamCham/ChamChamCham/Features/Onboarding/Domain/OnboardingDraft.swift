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
    var contact: String = ""
    var birthDate: Date?
    var managementType: ManagementType?
    var profileImageFileName: String?
    var cropIDs: [UUID] = []
    var farmName: String = ""
    var farmAddress: String = ""
    var farmLatitude: Double?
    var farmLongitude: Double?
}

enum ManagementType: String, CaseIterable, Codable {
    case general
    case corporation
    case none
}
