//
//  FarmingWorkReportStatistics.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ReportCoverage: Hashable, Sendable {
    let recordedCount: Int
    let targetCount: Int
}

struct ReportCountDistribution: Hashable, Sendable {
    let code: String
    let label: String
    let count: Int
    let ratePct: Decimal
}

struct ReportCommonStatistics: Hashable, Sendable {
    let recordCount: Int
    let firstWorkedOn: Date?
    let lastWorkedOn: Date?
    let workedDayCount: Int
    let averageIntervalDays: Decimal?
    let photoAttachedRecordCount: Int
    let photoAttachmentRatePct: Decimal?
    let weatherDistribution: [ReportCountDistribution]
    let averageTemperatureC: Decimal?
}

struct ReportPropagationStatistics: Hashable, Sendable {
    let code: String
    let label: String
    let recordCount: Int
    let recordRatePct: Decimal
    let totalQuantity: Decimal?
    let quantityUnit: String?
    let quantityCoverage: ReportCoverage
}

struct ReportAmountByUnitStatistics: Hashable, Sendable {
    let unit: String
    let amount: Decimal
    let coverage: ReportCoverage
}

struct ReportMaterialCategoryStatistics: Hashable, Sendable {
    let code: String
    let label: String
    let recordCount: Int
    let recordRatePct: Decimal
    let amountKg: Decimal
    let amountRatePct: Decimal
}

struct ReportCategoryMethodStatistics: Hashable, Sendable {
    let categoryCode: String
    let categoryLabel: String
    let methodCode: String
    let methodLabel: String
    let recordCount: Int
    let recordRatePct: Decimal
}

struct ReportCategoryAmountByUnitStatistics: Hashable, Sendable {
    let categoryCode: String
    let categoryLabel: String
    let unit: String
    let recordCount: Int
    let amount: Decimal
    let coverage: ReportCoverage
}

struct ReportTargetCount: Hashable, Sendable {
    let target: String
    let count: Int
}

struct ReportHarvestPartStatistics: Hashable, Sendable {
    let code: String
    let label: String
    let recordCount: Int
    let recordRatePct: Decimal
    let knownAmountKg: Decimal?
    let amountRatePct: Decimal?
    let amountCoverage: ReportCoverage
}

struct ReportGrowthPeriodRange: Hashable, Sendable {
    let minMonths: Int
    let maxMonths: Int
}

struct PlantingReportStatistics: Hashable, Sendable {
    let propagationMethods: [ReportPropagationStatistics]
}

struct WateringReportStatistics: Hashable, Sendable {
    let amountDistribution: [ReportCountDistribution]
    let methodDistribution: [ReportCountDistribution]
}

struct FertilizingReportStatistics: Hashable, Sendable {
    let totalAmountKg: Decimal?
    let averageAmountKg: Decimal?
    let amountCoverage: ReportCoverage
    let materialCategories: [ReportMaterialCategoryStatistics]
    let methodDistribution: [ReportCountDistribution]
    let categoryMethods: [ReportCategoryMethodStatistics]
}

struct PestControlReportStatistics: Hashable, Sendable {
    let categoryDistribution: [ReportCountDistribution]
    let pesticideAmounts: [ReportAmountByUnitStatistics]
    let categoryAmounts: [ReportCategoryAmountByUnitStatistics]
    let totalSprayAmountMl: Decimal?
    let sprayAmountCoverage: ReportCoverage
    let targets: [ReportTargetCount]
}

struct WeedingReportStatistics: Hashable, Sendable {
    let methodDistribution: [ReportCountDistribution]
}

struct HarvestReportStatistics: Hashable, Sendable {
    let totalAmountKg: Decimal?
    let averageAmountKg: Decimal?
    let amountCoverage: ReportCoverage
    let firstHarvestedOn: Date?
    let lastHarvestedOn: Date?
    let medicinalParts: [ReportHarvestPartStatistics]
    let finalGrowthPeriodMonths: Int?
    let growthPeriodRangeMonths: ReportGrowthPeriodRange?
}

struct FarmingWorkReportStatistics: Hashable, Sendable {
    let common: ReportCommonStatistics
    let planting: PlantingReportStatistics?
    let watering: WateringReportStatistics?
    let fertilizing: FertilizingReportStatistics?
    let pestControl: PestControlReportStatistics?
    let weeding: WeedingReportStatistics?
    let harvest: HarvestReportStatistics?

    var hasTypedStatistics: Bool {
        planting != nil || watering != nil || fertilizing != nil
            || pestControl != nil || weeding != nil || harvest != nil
    }
}
