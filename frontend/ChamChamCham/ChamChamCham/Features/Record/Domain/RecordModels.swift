//
//  RecordModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

/// 영농 활동 유형. Mirrors the backend `SaveRecordRequest.workType` / `RecordSummaryResponse.workType` enum.
///
/// Note: the Figma 영농 활동 filter shows nine chips including `가공`, but the deployed API enum has only these
/// eight (no processing/가공). We follow the deployed contract and omit 가공 until the backend adds it — see
/// `docs/figma/record/2026-07-13-record-filter-bottom-sheets.md`.
enum WorkType: String, Sendable, Hashable, CaseIterable {
    case planting = "PLANTING"
    case watering = "WATERING"
    case fertilizing = "FERTILIZING"
    case pestControl = "PEST_CONTROL"
    case weeding = "WEEDING"
    case pruning = "PRUNING"
    case harvest = "HARVEST"
    case etc = "ETC"

    /// Korean label. Authoritative wording confirmed by the product owner (2026-07-13); supersedes the earlier
    /// Figma filter-chip wording (거름·비료 / 병해충 방제 / 제초). See
    /// `docs/figma/record/2026-07-13-record-work-type-labels.md`.
    var label: String {
        switch self {
        case .planting: "심기"
        case .watering: "물주기"
        case .fertilizing: "비료 주기"
        case .pestControl: "병해충 관리"
        case .weeding: "잡초 관리"
        case .pruning: "가지·순 정리"
        case .harvest: "수확"
        case .etc: "기타"
        }
    }

    /// Base name of this work type's illustration in `Assets.xcassets/Illust` (see `AppIllustration`).
    /// Differs from `label`'s wording/punctuation in two cases (`비료주기`, `가지 순 정리`).
    var illustAssetName: String {
        switch self {
        case .planting: "심기"
        case .watering: "물주기"
        case .fertilizing: "비료주기"
        case .pestControl: "병해충 관리"
        case .weeding: "잡초 관리"
        case .pruning: "가지 순 정리"
        case .harvest: "수확"
        case .etc: "기타"
        }
    }
}

/// A crop the member is actively growing, sourced from `GET /members/me/farm-crops`. Used as the "진행중인 작물"
/// filter options. De-duplicated by `id` across farms in the repository.
struct ActiveCrop: Identifiable, Hashable, Sendable {
    let id: UUID
    let name: String
}

/// A row in the 영농 기록 list. Projection of the backend `RecordSummaryResponse`. `thumbnailUrl` is optional
/// because a record may have no photo (BR-RECORD-009: 사진은 보조 정보).
struct FarmingRecordSummary: Identifiable, Hashable, Sendable {
    let id: UUID
    let cropId: UUID
    let cropName: String
    let workType: WorkType
    let memoPreview: String
    let thumbnailUrl: String?
    let weatherCondition: String
    let weatherTemperature: Int
    let workedAt: Date
    /// workType별 2번째 뱃지용 상세값. 해당 활동유형에서만 값이 있고 나머지는 nil(PRUNING/ETC는 전부 nil → 2번째 뱃지 없음).
    let plantingMethod: PlantingMethod?
    let irrigationMethod: IrrigationMethod?
    let materialName: String?
    let pesticideName: String?
    let weedingMethod: WeedingMethod?
    let harvestAmount: Double?

    init(
        id: UUID,
        cropId: UUID,
        cropName: String,
        workType: WorkType,
        memoPreview: String,
        thumbnailUrl: String?,
        weatherCondition: String,
        weatherTemperature: Int,
        workedAt: Date,
        plantingMethod: PlantingMethod? = nil,
        irrigationMethod: IrrigationMethod? = nil,
        materialName: String? = nil,
        pesticideName: String? = nil,
        weedingMethod: WeedingMethod? = nil,
        harvestAmount: Double? = nil
    ) {
        self.id = id
        self.cropId = cropId
        self.cropName = cropName
        self.workType = workType
        self.memoPreview = memoPreview
        self.thumbnailUrl = thumbnailUrl
        self.weatherCondition = weatherCondition
        self.weatherTemperature = weatherTemperature
        self.workedAt = workedAt
        self.plantingMethod = plantingMethod
        self.irrigationMethod = irrigationMethod
        self.materialName = materialName
        self.pesticideName = pesticideName
        self.weedingMethod = weedingMethod
        self.harvestAmount = harvestAmount
    }

    /// 2번째 뱃지에 보여줄 workType별 상세 라벨. 값이 없으면(PRUNING/ETC 포함) nil — 뱃지 자체를 숨긴다.
    var detailBadgeLabel: String? {
        switch workType {
        case .planting: plantingMethod?.label
        case .watering: irrigationMethod?.label
        case .fertilizing: materialName
        case .pestControl: pesticideName
        case .weeding: weedingMethod?.label
        case .harvest: harvestAmount.map { amount in
            let rounded = amount == amount.rounded() ? String(Int(amount)) : String(amount)
            return "\(rounded)kg"
        }
        case .pruning, .etc: nil
        }
    }
}

/// One cursor page of records. `nextCursor == nil` means there are no more pages.
struct RecordPage: Sendable {
    let items: [FarmingRecordSummary]
    let nextCursor: String?
}

/// The three-axis filter for the record list. All axes optional/empty == 미적용.
///
/// 작물/영농 활동 are multi-select (deployed list endpoint takes `cropIds`/`workTypes` arrays).
struct RecordFilter: Equatable, Sendable {
    var cropIds: Set<UUID> = []
    var workTypes: Set<WorkType> = []
    var startDate: Date?
    var endDate: Date?

    var isEmpty: Bool {
        cropIds.isEmpty && workTypes.isEmpty && startDate == nil && endDate == nil
    }
}
