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
    var managementType: ManagementType?
    var profileImageFileName: String?
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
}

/// Raw values match the backend's `ManagementType` enum names exactly (`AuthRequests.CompleteOnboardingRequest.managementType`) —
/// `rawValue` is the entire wire-format mapping, so there's one source of truth instead of a separate translation table.
enum ManagementType: String, CaseIterable, Codable, Sendable {
    case agriculturalIndividual = "AGRICULTURAL_INDIVIDUAL"
    case agriculturalCorporation = "AGRICULTURAL_CORPORATION"
    case nonRegisteredFarmer = "NON_REGISTERED_FARMER"
}
