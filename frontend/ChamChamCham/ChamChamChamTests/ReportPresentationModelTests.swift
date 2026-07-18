//
//  ReportPresentationModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Report work type presentation")
struct ReportPresentationModelTests {
    @Test("planting shows unit-separated quantities and propagation distribution")
    func planting() {
        let statistics = baseStatistics(planting: PlantingReportStatistics(propagationMethods: [
            ReportPropagationStatistics(
                code: "SEED",
                label: "종자",
                recordCount: 2,
                recordRatePct: 67,
                totalQuantity: 120,
                quantityUnit: "g",
                quantityCoverage: coverage
            ),
            ReportPropagationStatistics(
                code: "SEEDLING",
                label: "모종",
                recordCount: 1,
                recordRatePct: 33,
                totalQuantity: 12,
                quantityUnit: "주",
                quantityCoverage: coverage
            ),
        ]))

        let presentation = ReportDetailPresentation(detail: detail(.planting, statistics: statistics))

        #expect(presentation.metrics.map(\.title) == ["총 작업 횟수", "종자 심은 양", "모종 심은 양"])
        #expect(presentation.metrics.map(\.value) == ["3회", "120g", "12주"])
        #expect(presentation.charts.map(\.title) == ["심기 방법"])
    }

    @Test("watering and fertilizing expose their supplied distributions")
    func wateringAndFertilizing() {
        let watering = ReportDetailPresentation(detail: detail(
            .watering,
            statistics: baseStatistics(watering: WateringReportStatistics(
                amountDistribution: [distribution("ENOUGH", "충분히", 3)],
                methodDistribution: [distribution("DRIP", "점적", 2)]
            ))
        ))
        let fertilizing = ReportDetailPresentation(detail: detail(
            .fertilizing,
            statistics: baseStatistics(fertilizing: FertilizingReportStatistics(
                totalAmountKg: 12.5,
                averageAmountKg: 6.25,
                amountCoverage: coverage,
                materialCategories: [ReportMaterialCategoryStatistics(
                    code: "COMPOST",
                    label: "퇴비",
                    recordCount: 2,
                    recordRatePct: 100,
                    amountKg: 12.5,
                    amountRatePct: 100
                )],
                methodDistribution: [distribution("SOIL", "토양", 2)],
                categoryMethods: []
            ))
        ))

        #expect(watering.metrics.contains { $0.title == "가장 자주 준 물의 양" && $0.value == "충분히" })
        #expect(watering.charts.map(\.title) == ["물의 양", "물 주는 방법"])
        #expect(fertilizing.metrics.contains { $0.title == "총 비료 사용량" && $0.value == "12.5kg" })
        #expect(fertilizing.charts.map(\.title) == ["비료 종류별 작업 횟수", "비료 종류별 사용량", "비료 주는 방법"])
    }

    @Test("pest control keeps pesticide units separate and maps targets")
    func pestControl() {
        let statistics = baseStatistics(pestControl: PestControlReportStatistics(
            categoryDistribution: [distribution("PEST", "해충", 2)],
            pesticideAmounts: [
                ReportAmountByUnitStatistics(unit: "g", amount: 10, coverage: coverage),
                ReportAmountByUnitStatistics(unit: "ml", amount: 250, coverage: coverage),
            ],
            categoryAmounts: [
                ReportCategoryAmountByUnitStatistics(
                    categoryCode: "PEST",
                    categoryLabel: "해충",
                    unit: "g",
                    recordCount: 1,
                    amount: 10,
                    coverage: coverage
                ),
                ReportCategoryAmountByUnitStatistics(
                    categoryCode: "DISEASE",
                    categoryLabel: "병",
                    unit: "ml",
                    recordCount: 1,
                    amount: 250,
                    coverage: coverage
                ),
            ],
            totalSprayAmountMl: 20,
            sprayAmountCoverage: coverage,
            targets: [ReportTargetCount(target: "진딧물", count: 2)]
        ))

        let presentation = ReportDetailPresentation(detail: detail(.pestControl, statistics: statistics))

        #expect(presentation.metrics.map(\.value).contains("10g"))
        #expect(presentation.metrics.map(\.value).contains("250ml"))
        #expect(presentation.metrics.map(\.value).contains("20mL"))
        #expect(presentation.charts.map(\.title) == [
            "관리 유형", "유형별 사용량 (g)", "유형별 사용량 (ml)", "관리 대상",
        ])
    }

    @Test("weeding, pruning, harvest, and etc only show supported sections")
    func remainingTypes() {
        let weeding = ReportDetailPresentation(detail: detail(
            .weeding,
            statistics: baseStatistics(weeding: WeedingReportStatistics(
                methodDistribution: [distribution("HAND", "손제초", 3)]
            ))
        ))
        let pruning = ReportDetailPresentation(detail: detail(.pruning, statistics: baseStatistics()))
        let harvest = ReportDetailPresentation(detail: detail(
            .harvest,
            statistics: baseStatistics(harvest: HarvestReportStatistics(
                totalAmountKg: 32.5,
                averageAmountKg: 16.25,
                amountCoverage: coverage,
                firstHarvestedOn: nil,
                lastHarvestedOn: nil,
                medicinalParts: [ReportHarvestPartStatistics(
                    code: "ROOT",
                    label: "뿌리",
                    recordCount: 2,
                    recordRatePct: 100,
                    knownAmountKg: 32.5,
                    amountRatePct: 100,
                    amountCoverage: coverage
                )],
                finalGrowthPeriodMonths: 5,
                growthPeriodRangeMonths: nil
            ))
        ))
        let etc = ReportDetailPresentation(detail: detail(.etc, statistics: baseStatistics()))

        #expect(weeding.charts.map(\.title) == ["잡초 관리 방법"])
        #expect(pruning.metrics.map(\.title) == ["총 작업 횟수"])
        #expect(pruning.charts.isEmpty)
        #expect(harvest.metrics.contains { $0.title == "총 수확량" && $0.value == "32.5kg" })
        #expect(harvest.metrics.contains { $0.title == "재배 기간" && $0.value == "5개월" })
        #expect(harvest.charts.map(\.title) == ["수확 부위"])
        #expect(etc.metrics.map(\.title) == ["총 작업 횟수"])
        #expect(etc.charts.isEmpty)
    }

    private var coverage: ReportCoverage { ReportCoverage(recordedCount: 1, targetCount: 1) }

    private func distribution(_ code: String, _ label: String, _ count: Int) -> ReportCountDistribution {
        ReportCountDistribution(code: code, label: label, count: count, ratePct: 100)
    }

    private func baseStatistics(
        planting: PlantingReportStatistics? = nil,
        watering: WateringReportStatistics? = nil,
        fertilizing: FertilizingReportStatistics? = nil,
        pestControl: PestControlReportStatistics? = nil,
        weeding: WeedingReportStatistics? = nil,
        harvest: HarvestReportStatistics? = nil
    ) -> FarmingWorkReportStatistics {
        FarmingWorkReportStatistics(
            common: ReportCommonStatistics(
                recordCount: 3,
                firstWorkedOn: nil,
                lastWorkedOn: nil,
                workedDayCount: 2,
                averageIntervalDays: 2,
                photoAttachedRecordCount: 1,
                photoAttachmentRatePct: 33,
                weatherDistribution: [],
                averageTemperatureC: nil
            ),
            planting: planting,
            watering: watering,
            fertilizing: fertilizing,
            pestControl: pestControl,
            weeding: weeding,
            harvest: harvest
        )
    }

    private func detail(
        _ workType: WorkType,
        statistics: FarmingWorkReportStatistics
    ) -> FarmingWorkReportDetail {
        FarmingWorkReportDetail(
            key: WorkReportKey(reportId: ReportFixtures.reportId, workType: workType),
            status: .completed,
            workTypeLabel: workType.label,
            farmId: ReportFixtures.farmId,
            farmName: "북쪽 밭",
            cropId: ReportFixtures.cropId,
            cropName: "황기",
            startsAt: .distantPast,
            endsAt: .now,
            statistics: statistics,
            feedback: nil
        )
    }
}
