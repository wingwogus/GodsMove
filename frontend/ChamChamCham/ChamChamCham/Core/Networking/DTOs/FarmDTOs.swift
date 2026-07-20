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

    /// 지적도에 없는 밭(하천둑 옆 등)을 사용자가 지도에 직접 그려 경계·면적을 잡은 경우.
    /// 주소는 사용자가 그린 폴리곤 중심 좌표를 V-World로 역지오코딩해 채운다(JUSO 검색이 아님).
    static let onboardingUserDrawnPolygon = FarmDataSourceDTO(
        address: "V_WORLD_REVERSE_GEOCODE",
        coordinate: "USER_DRAWN",
        parcel: "USER_DRAWN_POLYGON",
        landCharacteristic: nil
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
struct StandaloneFarmResponseDTO: Decodable, Sendable, Identifiable {
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

    var id: UUID { farmId }
}
