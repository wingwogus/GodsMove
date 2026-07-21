//
//  RecordDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

// Wire-shape mirrors of the backend farming-record responses. `workType` decodes as a plain `String` so an
// unknown future server case can't break decoding — mapping resolves it (unknown → `.etc`). `workedAt` is a
// `String` (the shared `JSONDecoder` sets no date strategy) parsed via `RecordDateParser` during mapping.

struct RecordPageResponseDTO: Decodable, Sendable {
    let items: [RecordSummaryResponseDTO]
    let nextCursor: String?
}

/// `POST /farming-records` 응답 (`RecordIdResponse` — `workType`도 오지만 id만 사용).
struct RecordIdResponseDTO: Decodable, Sendable {
    let id: UUID
}

struct RecordSummaryResponseDTO: Decodable, Sendable {
    let id: UUID
    let cropId: UUID
    let cropName: String
    let memoPreview: String
    let thumbnailUrl: String?
    let weatherCondition: String
    let weatherTemperature: Int
    let workType: String
    let workedAt: String
    /// workType별 2번째 뱃지용 상세값. 해당 활동유형에서만 값이 오고 나머지는 항상 null(PRUNING/ETC는 전부 null).
    let plantingMethod: String?
    let irrigationMethod: String?
    let materialName: String?
    let pesticideName: String?
    let weedingMethod: String?
    let harvestAmount: Double?
}

/// `GET /members/me/farm-crops` → one entry per farm, each carrying its crops.
struct FarmCropsResponseDTO: Decodable, Sendable {
    let farmId: UUID
    let farmName: String
    let crops: [FarmCropItemDTO]
}

struct FarmCropItemDTO: Decodable, Sendable {
    let id: UUID
    let name: String
    let externalNo: Int
    let usePartCategory: String
    let usePartCategoryLabel: String
}

// MARK: - Domain mapping

extension RecordSummaryResponseDTO {
    func toDomain() -> FarmingRecordSummary {
        FarmingRecordSummary(
            id: id,
            cropId: cropId,
            cropName: cropName,
            workType: WorkType(rawValue: workType) ?? .etc,
            memoPreview: memoPreview,
            thumbnailUrl: thumbnailUrl,
            weatherCondition: weatherCondition,
            weatherTemperature: weatherTemperature,
            workedAt: RecordDateParser.date(from: workedAt),
            plantingMethod: plantingMethod.flatMap(PlantingMethod.init(rawValue:)),
            irrigationMethod: irrigationMethod.flatMap(IrrigationMethod.init(rawValue:)),
            materialName: materialName,
            pesticideName: pesticideName,
            weedingMethod: weedingMethod.flatMap(WeedingMethod.init(rawValue:)),
            harvestAmount: harvestAmount
        )
    }
}

extension RecordPageResponseDTO {
    func toDomain() -> RecordPage {
        RecordPage(items: items.map { $0.toDomain() }, nextCursor: nextCursor)
    }
}

extension FarmCropItemDTO {
    func toDomain() -> ActiveCrop {
        ActiveCrop(id: id, name: name)
    }
}
