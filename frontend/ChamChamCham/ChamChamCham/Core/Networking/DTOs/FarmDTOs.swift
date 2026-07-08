//
//  FarmDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

struct FarmRequestDTO: Encodable, Sendable {
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

struct FarmDataSourceDTO: Codable, Sendable {
    let address: String?
    let coordinate: String?
    let parcel: String?
    let landCharacteristic: String?

    static let onboardingJusoVWorld = FarmDataSourceDTO(
        address: "JUSO",
        coordinate: "V_WORLD_ADDRESS",
        parcel: "V_WORLD_CADASTRAL",
        landCharacteristic: "V_WORLD_LAND_CHARACTERISTIC"
    )
}

struct FarmBoundaryCoordinateDTO: Codable, Sendable {
    let latitude: Double
    let longitude: Double
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
    let dataSource: FarmDataSourceDTO
}
