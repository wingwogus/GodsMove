//
//  FarmingWorkReportStatisticsDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ReportCoverageResponseDTO: Codable, Sendable {
    let recordedCount: Int
    let targetCount: Int

    func toDomain() -> ReportCoverage {
        ReportCoverage(recordedCount: recordedCount, targetCount: targetCount)
    }
}

struct ReportCountDistributionResponseDTO: Codable, Sendable {
    let code: String
    let label: String
    let count: Int
    let ratePct: Decimal

    func toDomain() -> ReportCountDistribution {
        ReportCountDistribution(code: code, label: label, count: count, ratePct: ratePct)
    }
}

struct ReportCommonStatisticsResponseDTO: Codable, Sendable {
    let recordCount: Int
    let firstWorkedOn: String?
    let lastWorkedOn: String?
    let workedDayCount: Int
    let averageIntervalDays: Decimal?
    let photoAttachedRecordCount: Int
    let photoAttachmentRatePct: Decimal?
    let weatherDistribution: [ReportCountDistributionResponseDTO]
    let averageTemperatureC: Decimal?

    func toDomain() -> ReportCommonStatistics {
        ReportCommonStatistics(
            recordCount: recordCount,
            firstWorkedOn: ReportDateParser.localDate(from: firstWorkedOn),
            lastWorkedOn: ReportDateParser.localDate(from: lastWorkedOn),
            workedDayCount: workedDayCount,
            averageIntervalDays: averageIntervalDays,
            photoAttachedRecordCount: photoAttachedRecordCount,
            photoAttachmentRatePct: photoAttachmentRatePct,
            weatherDistribution: weatherDistribution.map { $0.toDomain() },
            averageTemperatureC: averageTemperatureC
        )
    }
}

struct ReportPropagationStatisticsResponseDTO: Codable, Sendable {
    let code: String
    let label: String
    let recordCount: Int
    let recordRatePct: Decimal
    let totalQuantity: Decimal?
    let quantityUnit: String?
    let quantityCoverage: ReportCoverageResponseDTO

    func toDomain() -> ReportPropagationStatistics {
        ReportPropagationStatistics(
            code: code,
            label: label,
            recordCount: recordCount,
            recordRatePct: recordRatePct,
            totalQuantity: totalQuantity,
            quantityUnit: quantityUnit,
            quantityCoverage: quantityCoverage.toDomain()
        )
    }
}

struct ReportAmountByUnitResponseDTO: Codable, Sendable {
    let unit: String
    let amount: Decimal
    let coverage: ReportCoverageResponseDTO

    func toDomain() -> ReportAmountByUnitStatistics {
        ReportAmountByUnitStatistics(unit: unit, amount: amount, coverage: coverage.toDomain())
    }
}

struct ReportMaterialCategoryStatisticsResponseDTO: Codable, Sendable {
    let code: String
    let label: String
    let recordCount: Int
    let recordRatePct: Decimal
    let amountKg: Decimal
    let amountRatePct: Decimal

    func toDomain() -> ReportMaterialCategoryStatistics {
        ReportMaterialCategoryStatistics(
            code: code,
            label: label,
            recordCount: recordCount,
            recordRatePct: recordRatePct,
            amountKg: amountKg,
            amountRatePct: amountRatePct
        )
    }
}

struct ReportCategoryMethodStatisticsResponseDTO: Codable, Sendable {
    let categoryCode: String
    let categoryLabel: String
    let methodCode: String
    let methodLabel: String
    let recordCount: Int
    let recordRatePct: Decimal

    func toDomain() -> ReportCategoryMethodStatistics {
        ReportCategoryMethodStatistics(
            categoryCode: categoryCode,
            categoryLabel: categoryLabel,
            methodCode: methodCode,
            methodLabel: methodLabel,
            recordCount: recordCount,
            recordRatePct: recordRatePct
        )
    }
}

struct ReportCategoryAmountByUnitResponseDTO: Codable, Sendable {
    let categoryCode: String
    let categoryLabel: String
    let unit: String
    let recordCount: Int
    let amount: Decimal
    let coverage: ReportCoverageResponseDTO

    func toDomain() -> ReportCategoryAmountByUnitStatistics {
        ReportCategoryAmountByUnitStatistics(
            categoryCode: categoryCode,
            categoryLabel: categoryLabel,
            unit: unit,
            recordCount: recordCount,
            amount: amount,
            coverage: coverage.toDomain()
        )
    }
}

struct ReportTargetCountResponseDTO: Codable, Sendable {
    let target: String
    let count: Int

    func toDomain() -> ReportTargetCount {
        ReportTargetCount(target: target, count: count)
    }
}

struct ReportHarvestPartStatisticsResponseDTO: Codable, Sendable {
    let code: String
    let label: String
    let recordCount: Int
    let recordRatePct: Decimal
    let knownAmountKg: Decimal?
    let amountRatePct: Decimal?
    let amountCoverage: ReportCoverageResponseDTO

    func toDomain() -> ReportHarvestPartStatistics {
        ReportHarvestPartStatistics(
            code: code,
            label: label,
            recordCount: recordCount,
            recordRatePct: recordRatePct,
            knownAmountKg: knownAmountKg,
            amountRatePct: amountRatePct,
            amountCoverage: amountCoverage.toDomain()
        )
    }
}

struct ReportGrowthPeriodRangeResponseDTO: Codable, Sendable {
    let minMonths: Int
    let maxMonths: Int

    func toDomain() -> ReportGrowthPeriodRange {
        ReportGrowthPeriodRange(minMonths: minMonths, maxMonths: maxMonths)
    }
}

struct PlantingReportStatisticsResponseDTO: Codable, Sendable {
    let common: ReportCommonStatisticsResponseDTO
    let propagationMethods: [ReportPropagationStatisticsResponseDTO]

    private enum CodingKeys: String, CodingKey { case propagationMethods }

    init(from decoder: Decoder) throws {
        common = try ReportCommonStatisticsResponseDTO(from: decoder)
        propagationMethods = try decoder.container(keyedBy: CodingKeys.self)
            .decode([ReportPropagationStatisticsResponseDTO].self, forKey: .propagationMethods)
    }

    func encode(to encoder: Encoder) throws {
        try common.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(propagationMethods, forKey: .propagationMethods)
    }

    func toDomain() -> PlantingReportStatistics {
        PlantingReportStatistics(propagationMethods: propagationMethods.map { $0.toDomain() })
    }
}

struct WateringReportStatisticsResponseDTO: Codable, Sendable {
    let common: ReportCommonStatisticsResponseDTO
    let amountDistribution: [ReportCountDistributionResponseDTO]
    let methodDistribution: [ReportCountDistributionResponseDTO]

    private enum CodingKeys: String, CodingKey { case amountDistribution, methodDistribution }

    init(from decoder: Decoder) throws {
        common = try ReportCommonStatisticsResponseDTO(from: decoder)
        let container = try decoder.container(keyedBy: CodingKeys.self)
        amountDistribution = try container.decode([ReportCountDistributionResponseDTO].self, forKey: .amountDistribution)
        methodDistribution = try container.decode([ReportCountDistributionResponseDTO].self, forKey: .methodDistribution)
    }

    func encode(to encoder: Encoder) throws {
        try common.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(amountDistribution, forKey: .amountDistribution)
        try container.encode(methodDistribution, forKey: .methodDistribution)
    }

    func toDomain() -> WateringReportStatistics {
        WateringReportStatistics(
            amountDistribution: amountDistribution.map { $0.toDomain() },
            methodDistribution: methodDistribution.map { $0.toDomain() }
        )
    }
}

struct FertilizingReportStatisticsResponseDTO: Codable, Sendable {
    let common: ReportCommonStatisticsResponseDTO
    let totalAmountKg: Decimal?
    let averageAmountKg: Decimal?
    let amountCoverage: ReportCoverageResponseDTO
    let materialCategories: [ReportMaterialCategoryStatisticsResponseDTO]
    let methodDistribution: [ReportCountDistributionResponseDTO]
    let categoryMethods: [ReportCategoryMethodStatisticsResponseDTO]

    private enum CodingKeys: String, CodingKey {
        case totalAmountKg, averageAmountKg, amountCoverage, materialCategories, methodDistribution, categoryMethods
    }

    init(from decoder: Decoder) throws {
        common = try ReportCommonStatisticsResponseDTO(from: decoder)
        let container = try decoder.container(keyedBy: CodingKeys.self)
        totalAmountKg = try container.decodeIfPresent(Decimal.self, forKey: .totalAmountKg)
        averageAmountKg = try container.decodeIfPresent(Decimal.self, forKey: .averageAmountKg)
        amountCoverage = try container.decode(ReportCoverageResponseDTO.self, forKey: .amountCoverage)
        materialCategories = try container.decode([ReportMaterialCategoryStatisticsResponseDTO].self, forKey: .materialCategories)
        methodDistribution = try container.decode([ReportCountDistributionResponseDTO].self, forKey: .methodDistribution)
        categoryMethods = try container.decode([ReportCategoryMethodStatisticsResponseDTO].self, forKey: .categoryMethods)
    }

    func encode(to encoder: Encoder) throws {
        try common.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(totalAmountKg, forKey: .totalAmountKg)
        try container.encodeIfPresent(averageAmountKg, forKey: .averageAmountKg)
        try container.encode(amountCoverage, forKey: .amountCoverage)
        try container.encode(materialCategories, forKey: .materialCategories)
        try container.encode(methodDistribution, forKey: .methodDistribution)
        try container.encode(categoryMethods, forKey: .categoryMethods)
    }

    func toDomain() -> FertilizingReportStatistics {
        FertilizingReportStatistics(
            totalAmountKg: totalAmountKg,
            averageAmountKg: averageAmountKg,
            amountCoverage: amountCoverage.toDomain(),
            materialCategories: materialCategories.map { $0.toDomain() },
            methodDistribution: methodDistribution.map { $0.toDomain() },
            categoryMethods: categoryMethods.map { $0.toDomain() }
        )
    }
}

struct PestControlReportStatisticsResponseDTO: Codable, Sendable {
    let common: ReportCommonStatisticsResponseDTO
    let categoryDistribution: [ReportCountDistributionResponseDTO]
    let pesticideAmounts: [ReportAmountByUnitResponseDTO]
    let categoryAmounts: [ReportCategoryAmountByUnitResponseDTO]
    let totalSprayAmountMl: Decimal?
    let sprayAmountCoverage: ReportCoverageResponseDTO
    let targets: [ReportTargetCountResponseDTO]

    private enum CodingKeys: String, CodingKey {
        case categoryDistribution, pesticideAmounts, categoryAmounts, totalSprayAmountMl, sprayAmountCoverage, targets
    }

    init(from decoder: Decoder) throws {
        common = try ReportCommonStatisticsResponseDTO(from: decoder)
        let container = try decoder.container(keyedBy: CodingKeys.self)
        categoryDistribution = try container.decode([ReportCountDistributionResponseDTO].self, forKey: .categoryDistribution)
        pesticideAmounts = try container.decode([ReportAmountByUnitResponseDTO].self, forKey: .pesticideAmounts)
        categoryAmounts = try container.decode([ReportCategoryAmountByUnitResponseDTO].self, forKey: .categoryAmounts)
        totalSprayAmountMl = try container.decodeIfPresent(Decimal.self, forKey: .totalSprayAmountMl)
        sprayAmountCoverage = try container.decode(ReportCoverageResponseDTO.self, forKey: .sprayAmountCoverage)
        targets = try container.decode([ReportTargetCountResponseDTO].self, forKey: .targets)
    }

    func encode(to encoder: Encoder) throws {
        try common.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(categoryDistribution, forKey: .categoryDistribution)
        try container.encode(pesticideAmounts, forKey: .pesticideAmounts)
        try container.encode(categoryAmounts, forKey: .categoryAmounts)
        try container.encodeIfPresent(totalSprayAmountMl, forKey: .totalSprayAmountMl)
        try container.encode(sprayAmountCoverage, forKey: .sprayAmountCoverage)
        try container.encode(targets, forKey: .targets)
    }

    func toDomain() -> PestControlReportStatistics {
        PestControlReportStatistics(
            categoryDistribution: categoryDistribution.map { $0.toDomain() },
            pesticideAmounts: pesticideAmounts.map { $0.toDomain() },
            categoryAmounts: categoryAmounts.map { $0.toDomain() },
            totalSprayAmountMl: totalSprayAmountMl,
            sprayAmountCoverage: sprayAmountCoverage.toDomain(),
            targets: targets.map { $0.toDomain() }
        )
    }
}

struct WeedingReportStatisticsResponseDTO: Codable, Sendable {
    let common: ReportCommonStatisticsResponseDTO
    let methodDistribution: [ReportCountDistributionResponseDTO]

    private enum CodingKeys: String, CodingKey { case methodDistribution }

    init(from decoder: Decoder) throws {
        common = try ReportCommonStatisticsResponseDTO(from: decoder)
        methodDistribution = try decoder.container(keyedBy: CodingKeys.self)
            .decode([ReportCountDistributionResponseDTO].self, forKey: .methodDistribution)
    }

    func encode(to encoder: Encoder) throws {
        try common.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(methodDistribution, forKey: .methodDistribution)
    }

    func toDomain() -> WeedingReportStatistics {
        WeedingReportStatistics(methodDistribution: methodDistribution.map { $0.toDomain() })
    }
}

struct HarvestReportStatisticsResponseDTO: Codable, Sendable {
    let common: ReportCommonStatisticsResponseDTO
    let totalAmountKg: Decimal?
    let averageAmountKg: Decimal?
    let amountCoverage: ReportCoverageResponseDTO
    let firstHarvestedOn: String?
    let lastHarvestedOn: String?
    let medicinalParts: [ReportHarvestPartStatisticsResponseDTO]
    let finalGrowthPeriodMonths: Int?
    let growthPeriodRangeMonths: ReportGrowthPeriodRangeResponseDTO?

    private enum CodingKeys: String, CodingKey {
        case totalAmountKg, averageAmountKg, amountCoverage, firstHarvestedOn, lastHarvestedOn
        case medicinalParts, finalGrowthPeriodMonths, growthPeriodRangeMonths
    }

    init(from decoder: Decoder) throws {
        common = try ReportCommonStatisticsResponseDTO(from: decoder)
        let container = try decoder.container(keyedBy: CodingKeys.self)
        totalAmountKg = try container.decodeIfPresent(Decimal.self, forKey: .totalAmountKg)
        averageAmountKg = try container.decodeIfPresent(Decimal.self, forKey: .averageAmountKg)
        amountCoverage = try container.decode(ReportCoverageResponseDTO.self, forKey: .amountCoverage)
        firstHarvestedOn = try container.decodeIfPresent(String.self, forKey: .firstHarvestedOn)
        lastHarvestedOn = try container.decodeIfPresent(String.self, forKey: .lastHarvestedOn)
        medicinalParts = try container.decode([ReportHarvestPartStatisticsResponseDTO].self, forKey: .medicinalParts)
        finalGrowthPeriodMonths = try container.decodeIfPresent(Int.self, forKey: .finalGrowthPeriodMonths)
        growthPeriodRangeMonths = try container.decodeIfPresent(ReportGrowthPeriodRangeResponseDTO.self, forKey: .growthPeriodRangeMonths)
    }

    func encode(to encoder: Encoder) throws {
        try common.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(totalAmountKg, forKey: .totalAmountKg)
        try container.encodeIfPresent(averageAmountKg, forKey: .averageAmountKg)
        try container.encode(amountCoverage, forKey: .amountCoverage)
        try container.encodeIfPresent(firstHarvestedOn, forKey: .firstHarvestedOn)
        try container.encodeIfPresent(lastHarvestedOn, forKey: .lastHarvestedOn)
        try container.encode(medicinalParts, forKey: .medicinalParts)
        try container.encodeIfPresent(finalGrowthPeriodMonths, forKey: .finalGrowthPeriodMonths)
        try container.encodeIfPresent(growthPeriodRangeMonths, forKey: .growthPeriodRangeMonths)
    }

    func toDomain() -> HarvestReportStatistics {
        HarvestReportStatistics(
            totalAmountKg: totalAmountKg,
            averageAmountKg: averageAmountKg,
            amountCoverage: amountCoverage.toDomain(),
            firstHarvestedOn: ReportDateParser.localDate(from: firstHarvestedOn),
            lastHarvestedOn: ReportDateParser.localDate(from: lastHarvestedOn),
            medicinalParts: medicinalParts.map { $0.toDomain() },
            finalGrowthPeriodMonths: finalGrowthPeriodMonths,
            growthPeriodRangeMonths: growthPeriodRangeMonths?.toDomain()
        )
    }
}
