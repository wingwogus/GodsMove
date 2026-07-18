//
//  ReportPreviewFixtures.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

#if DEBUG
import Foundation
import SwiftUI

enum ReportPreviewFixtures {
    static let reportId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    static let farmId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
    static let cropId = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!

    static func detail(
        workType: WorkType,
        cycleStatus: ReportCycleStatus = .completed,
        feedbackState: ReportFeedbackState = .ready
    ) -> FarmingWorkReportDetail {
        FarmingWorkReportDetail(
            key: WorkReportKey(reportId: reportId, workType: workType),
            status: cycleStatus,
            workTypeLabel: workType.label,
            farmId: farmId,
            farmName: "참참 농장",
            cropId: cropId,
            cropName: "황기",
            startsAt: Date(timeIntervalSince1970: 1_712_000_000),
            endsAt: cycleStatus == .active ? nil : Date(timeIntervalSince1970: 1_730_000_000),
            statistics: statistics(workType),
            feedback: cycleStatus == .active
                ? nil
                : ReportFeedbackStatus(
                    state: feedbackState,
                    content: feedbackState == .ready ? coachingContent : nil
                )
        )
    }

    @MainActor
    static func repository(
        workType: WorkType,
        cycleStatus: ReportCycleStatus = .completed,
        feedbackState: ReportFeedbackState = .ready,
        source: ReportResource<FarmingWorkReportDetail>.Source = .network
    ) -> ReportPreviewRepository {
        ReportPreviewRepository(
            detail: detail(
                workType: workType,
                cycleStatus: cycleStatus,
                feedbackState: feedbackState
            ),
            feedbackState: feedbackState,
            source: source
        )
    }

    private static let coachingContent = ReportFeedbackContent(
        summary: "전체 작업 흐름이 안정적이고 기록도 꾸준해요.",
        comparisons: ["이전 재배 주기보다 작업 간격이 일정해졌어요."],
        strengths: ["적절한 시기에 작업했어요.", "사진과 메모를 꾸준히 남겼어요."],
        improvements: ["사용량을 함께 기록하면 더 정확한 코칭을 받을 수 있어요."],
        nextActions: ["다음 작업 예정일을 미리 정해보세요."]
    )

    private static let coverage = ReportCoverage(recordedCount: 3, targetCount: 3)
    private static let distributions = [
        ReportCountDistribution(code: "A", label: "충분히", count: 5, ratePct: 50),
        ReportCountDistribution(code: "B", label: "적당히", count: 3, ratePct: 30),
        ReportCountDistribution(code: "C", label: "조금", count: 2, ratePct: 20),
    ]

    private static func statistics(_ workType: WorkType) -> FarmingWorkReportStatistics {
        let common = ReportCommonStatistics(
            recordCount: 10,
            firstWorkedOn: Date(timeIntervalSince1970: 1_712_000_000),
            lastWorkedOn: Date(timeIntervalSince1970: 1_720_000_000),
            workedDayCount: 8,
            averageIntervalDays: 4,
            photoAttachedRecordCount: 7,
            photoAttachmentRatePct: 70,
            weatherDistribution: [],
            averageTemperatureC: 21.5
        )
        return FarmingWorkReportStatistics(
            common: common,
            planting: workType == .planting ? PlantingReportStatistics(
                plantingMethodDistribution: [
                    ReportCountDistribution(code: "SEED", label: "씨앗 심기", count: 6, ratePct: 60),
                    ReportCountDistribution(code: "SEEDLING", label: "모종 심기", count: 4, ratePct: 40),
                ],
                propagationMethods: [
                    ReportPropagationStatistics(
                        code: "SEED",
                        label: "종자",
                        recordCount: 6,
                        recordRatePct: 60,
                        totalQuantity: 120,
                        quantityUnit: "g",
                        quantityCoverage: coverage
                    ),
                    ReportPropagationStatistics(
                        code: "SEEDLING",
                        label: "모종",
                        recordCount: 4,
                        recordRatePct: 40,
                        totalQuantity: 40,
                        quantityUnit: "주",
                        quantityCoverage: coverage
                    ),
                ]
            ) : nil,
            watering: workType == .watering ? WateringReportStatistics(
                amountDistribution: distributions + [
                    ReportCountDistribution(code: "D", label: "흠뻑", count: 1, ratePct: 10)
                ],
                methodDistribution: distributions
            ) : nil,
            fertilizing: workType == .fertilizing ? FertilizingReportStatistics(
                totalAmountKg: 18.5,
                averageAmountKg: 3.7,
                amountCoverage: coverage,
                materialCategories: [
                    ReportMaterialCategoryStatistics(
                        code: "COMPOST",
                        label: "퇴비",
                        recordCount: 5,
                        recordRatePct: 50,
                        amountKg: 12,
                        amountRatePct: 65
                    ),
                    ReportMaterialCategoryStatistics(
                        code: "ORGANIC",
                        label: "유기질",
                        recordCount: 3,
                        recordRatePct: 30,
                        amountKg: 6.5,
                        amountRatePct: 35
                    ),
                ],
                methodDistribution: distributions,
                categoryMethods: []
            ) : nil,
            pestControl: workType == .pestControl ? PestControlReportStatistics(
                categoryDistribution: distributions,
                pesticideAmounts: [
                    ReportAmountByUnitStatistics(unit: "ml", amount: 250, coverage: coverage)
                ],
                categoryAmounts: [
                    ReportCategoryAmountByUnitStatistics(
                        categoryCode: "PEST",
                        categoryLabel: "해충",
                        unit: "ml",
                        recordCount: 2,
                        amount: 150,
                        coverage: coverage
                    ),
                    ReportCategoryAmountByUnitStatistics(
                        categoryCode: "DISEASE",
                        categoryLabel: "병",
                        unit: "ml",
                        recordCount: 1,
                        amount: 100,
                        coverage: coverage
                    ),
                ],
                totalSprayAmountMl: 20,
                sprayAmountCoverage: coverage,
                targets: [
                    ReportTargetCount(target: "진딧물", count: 3),
                    ReportTargetCount(target: "흰가루병", count: 2),
                ]
            ) : nil,
            weeding: workType == .weeding
                ? WeedingReportStatistics(methodDistribution: distributions)
                : nil,
            harvest: workType == .harvest ? HarvestReportStatistics(
                totalAmountKg: 42.5,
                averageAmountKg: 8.5,
                amountCoverage: coverage,
                firstHarvestedOn: Date(timeIntervalSince1970: 1_725_000_000),
                lastHarvestedOn: Date(timeIntervalSince1970: 1_730_000_000),
                medicinalParts: [
                    ReportHarvestPartStatistics(
                        code: "ROOT",
                        label: "뿌리",
                        recordCount: 6,
                        recordRatePct: 60,
                        knownAmountKg: 30,
                        amountRatePct: 70,
                        amountCoverage: coverage
                    ),
                    ReportHarvestPartStatistics(
                        code: "LEAF",
                        label: "잎",
                        recordCount: 4,
                        recordRatePct: 40,
                        knownAmountKg: 12.5,
                        amountRatePct: 30,
                        amountCoverage: coverage
                    ),
                ],
                finalGrowthPeriodMonths: 6,
                growthPeriodRangeMonths: ReportGrowthPeriodRange(minMonths: 5, maxMonths: 7),
                growthPeriodDistribution: [
                    ReportCountDistribution(code: "24", label: "24개월", count: 2, ratePct: 60),
                    ReportCountDistribution(code: "18", label: "18개월", count: 1, ratePct: 40),
                ]
            ) : nil
        )
    }
}

@MainActor
final class ReportPreviewRepository: ReportRepository {
    let detail: FarmingWorkReportDetail
    let feedbackState: ReportFeedbackState
    let source: ReportResource<FarmingWorkReportDetail>.Source

    init(
        detail: FarmingWorkReportDetail,
        feedbackState: ReportFeedbackState,
        source: ReportResource<FarmingWorkReportDetail>.Source
    ) {
        self.detail = detail
        self.feedbackState = feedbackState
        self.source = source
    }

    func fetchReports(
        filter: ReportFilter,
        cursor: String?,
        size: Int
    ) async throws -> ReportResource<FarmingWorkReportPage> {
        let summary = FarmingWorkReportSummary(
            key: detail.key,
            status: detail.status,
            farmId: detail.farmId,
            farmName: detail.farmName,
            cropId: detail.cropId,
            cropName: detail.cropName,
            startsAt: detail.startsAt,
            endsAt: detail.endsAt,
            workTypeLabel: detail.workTypeLabel,
            recordCount: detail.statistics.common.recordCount,
            lastWorkedOn: detail.statistics.common.lastWorkedOn,
            thumbnailUrl: nil
        )
        return ReportResource(value: FarmingWorkReportPage(items: [summary], nextCursor: nil), source: .network)
    }

    func fetchDetail(_ key: WorkReportKey) async throws -> ReportResource<FarmingWorkReportDetail> {
        ReportResource(value: detail, source: source)
    }

    func loadCachedDetail(_ key: WorkReportKey) -> ReportResource<FarmingWorkReportDetail>? {
        guard case .cache = source else { return nil }
        return ReportResource(value: detail, source: source)
    }

    func fetchFeedback(
        reportId: UUID,
        workType: WorkType
    ) async throws -> ReportResource<ReportFeedbackItem?> {
        guard detail.status == .completed else {
            return ReportResource(value: nil, source: .network)
        }
        let content = detail.feedback?.content
        let item = ReportFeedbackItem(
            id: UUID(uuidString: "44444444-4444-4444-4444-444444444444")!,
            workType: workType,
            state: feedbackState,
            inputPrepared: true,
            failureCode: nil,
            content: content,
            createdAt: detail.startsAt,
            updatedAt: detail.endsAt ?? detail.startsAt
        )
        let feedbackSource: ReportResource<ReportFeedbackItem?>.Source
        switch source {
        case .network:
            feedbackSource = .network
        case let .cache(updatedAt):
            feedbackSource = .cache(updatedAt: updatedAt)
        }
        return ReportResource(value: item, source: feedbackSource)
    }

    func regenerate(_ key: WorkReportKey) async throws -> ReportFeedbackItem {
        ReportFeedbackItem(
            id: UUID(uuidString: "55555555-5555-5555-5555-555555555555")!,
            workType: key.workType,
            state: .pending,
            inputPrepared: true,
            failureCode: nil,
            content: nil,
            createdAt: detail.startsAt,
            updatedAt: detail.endsAt ?? detail.startsAt
        )
    }
}

#Preview("리포트 상세 · 수확 · READY") {
    let repository = ReportPreviewFixtures.repository(workType: .harvest)
    NavigationStack {
        ReportDetailView(key: repository.detail.key, repository: repository)
    }
}

#Preview("리포트 상세 · ACTIVE") {
    let repository = ReportPreviewFixtures.repository(
        workType: .planting,
        cycleStatus: .active,
        feedbackState: .pending
    )
    NavigationStack {
        ReportDetailView(key: repository.detail.key, repository: repository)
    }
}
#endif
