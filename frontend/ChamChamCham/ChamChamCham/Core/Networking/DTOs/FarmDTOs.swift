//
//  FarmDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

struct FarmDraftRequestDTO: Encodable, Sendable {
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
}

struct SaveFarmRequestDTO: Codable, Sendable, Equatable {
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

struct FarmDataSourceDTO: Codable, Sendable, Equatable {
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

struct FarmBoundaryCoordinateDTO: Codable, Sendable, Equatable {
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

/// The standalone farm controller and onboarding controller both publish a schema named `FarmResponse`, but their
/// payloads differ. Keep this endpoint-specific DTO separate until the backend gives those OpenAPI schemas unique
/// names: standalone CRUD uses `farmId` and embeds crops, while onboarding still uses `id` above.
struct StandaloneFarmResponseDTO: Decodable, Sendable {
    let farmId: UUID
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
    let crops: [CropResponseDTO]
}
