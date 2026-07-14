//
//  RecordComposeDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

// MARK: - 생성 요청 (`POST /farming-records`, `SaveRecordRequest` — origin dev shape)

struct SaveRecordRequestDTO: Encodable, Sendable {
    let farmId: UUID
    let cropId: UUID
    let workType: String
    let workedAt: String   // LocalDateTime "yyyy-MM-dd'T'HH:mm:ss"
    let weatherCondition: String
    let weatherTemperature: Int
    let memo: String
    let planting: PlantingDetailRequestDTO?
    let watering: WateringDetailRequestDTO?
    let fertilizing: FertilizingDetailRequestDTO?
    let pestControl: PestControlDetailRequestDTO?
    let weeding: WeedingDetailRequestDTO?
    let harvest: HarvestDetailRequestDTO?
    let mediaIds: [UUID]
    let entryMode: String
}

struct PlantingDetailRequestDTO: Encodable, Sendable {
    let plantingMethod: String
    let seedAmount: Double?
    let seedAmountUnit: String?
    let seedlingCount: Int?
    let seedlingUnit: String?
    let propagationMethod: String?
}

struct WateringDetailRequestDTO: Encodable, Sendable {
    let irrigationAmount: String?
    let irrigationMethod: String?
}

struct FertilizingDetailRequestDTO: Encodable, Sendable {
    let materialName: String
    let amount: Double
    let amountUnit: String
    let applicationMethod: String?
}

struct PestControlDetailRequestDTO: Encodable, Sendable {
    let pesticideId: UUID
    let pesticideAmount: Double
    let pesticideAmountUnit: String
    let totalSprayAmount: Double
    let totalSprayAmountUnit: String
    let pestId: UUID?
}

struct WeedingDetailRequestDTO: Encodable, Sendable {
    let weedingMethod: String?
}

struct HarvestDetailRequestDTO: Encodable, Sendable {
    let harvestAmount: Double?
    let harvestAmountUnknown: Bool
    let medicinalPart: String?
    let harvestSource: String
    let growthPeriod: Int?
    let growthPeriodUnit: String?
    let isLastHarvest: Bool
}

// MARK: - 카탈로그 / 날씨 응답

struct PesticidePageResponseDTO: Decodable, Sendable {
    let items: [PesticideSummaryResponseDTO]
    let nextCursor: String?
}

struct PesticideSummaryResponseDTO: Decodable, Sendable {
    let id: UUID
    let itemName: String
    let brandName: String

    func toDomain() -> Pesticide { Pesticide(id: id, itemName: itemName, brandName: brandName) }
}

struct PestSummaryResponseDTO: Decodable, Sendable {
    let id: UUID
    let name: String

    func toDomain() -> Pest { Pest(id: id, name: name) }
}

struct CurrentWeatherResponseDTO: Decodable, Sendable {
    let temperature: Int
    let weatherCondition: String

    func toDomain() -> CurrentWeather {
        CurrentWeather(temperature: temperature, condition: weatherCondition)
    }
}

// MARK: - farm-crops (그룹) 매핑

extension FarmCropsResponseDTO {
    func toFarmWithCrops() -> FarmWithCrops {
        FarmWithCrops(farmId: farmId, farmName: farmName, crops: crops.map { $0.toDomain() })
    }
}
