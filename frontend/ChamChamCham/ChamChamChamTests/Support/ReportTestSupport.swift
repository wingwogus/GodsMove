//
//  ReportTestSupport.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
@testable import ChamChamCham

enum ReportFixtures {
    static let reportId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    static let farmId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
    static let cropId = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!

    static func item(
        reportId: UUID = reportId,
        workType: WorkType = .planting,
        cropName: String = "황기"
    ) -> FarmingWorkReportItemResponseDTO {
        FarmingWorkReportItemResponseDTO(
            reportId: reportId,
            status: "COMPLETED",
            farmId: farmId,
            farmName: "북쪽 밭",
            cropId: cropId,
            cropName: cropName,
            startsAt: "2026-04-01T09:30:00",
            endsAt: "2026-10-31T18:00:00",
            workType: workType.rawValue,
            workTypeLabel: workTypeLabel(workType),
            recordCount: 2,
            lastWorkedOn: "2026-10-30",
            thumbnailUrl: nil
        )
    }

    static func page(
        items: [FarmingWorkReportItemResponseDTO] = [item()],
        nextCursor: String? = nil
    ) -> FarmingWorkReportPageResponseDTO {
        FarmingWorkReportPageResponseDTO(items: items, nextCursor: nextCursor)
    }

    static func detail(
        reportId: UUID = reportId,
        workType: WorkType = .planting
    ) -> FarmingWorkReportDetailResponseDTO {
        FarmingWorkReportDetailResponseDTO(
            reportId: reportId,
            status: "COMPLETED",
            workType: workType.rawValue,
            workTypeLabel: workTypeLabel(workType),
            farmId: farmId,
            farmName: "북쪽 밭",
            cropId: cropId,
            cropName: "황기",
            startsAt: "2026-04-01T09:30:00",
            endsAt: "2026-10-31T18:00:00",
            statistics: FarmingWorkReportStatisticsResponseDTO(
                common: commonStatistics,
                planting: nil,
                watering: nil,
                fertilizing: nil,
                pestControl: nil,
                weeding: nil,
                harvest: nil
            ),
            feedback: nil
        )
    }

    static func feedbackItem(
        reportId: UUID = reportId,
        workType: WorkType = .planting,
        status: String = "READY"
    ) -> ReportFeedbackItemResponseDTO {
        ReportFeedbackItemResponseDTO(
            feedbackId: UUID(),
            workType: workType.rawValue,
            status: status,
            inputPrepared: true,
            failureCode: nil,
            feedback: status == "READY"
                ? ReportFeedbackContentResponseDTO(
                    summary: "좋은 흐름이에요",
                    comparisons: [],
                    strengths: [ReportFeedbackBulletResponseDTO(text: "간격이 안정적이에요")],
                    improvements: [],
                    nextActions: []
                )
                : nil,
            createdAt: "2026-07-16T09:00:00",
            updatedAt: "2026-07-16T09:05:00"
        )
    }

    static func feedbackList(
        reportId: UUID = reportId,
        feedbacks: [ReportFeedbackItemResponseDTO] = [feedbackItem()]
    ) -> ReportFeedbackListResponseDTO {
        ReportFeedbackListResponseDTO(reportId: reportId, feedbacks: feedbacks)
    }

    private static let commonStatistics = ReportCommonStatisticsResponseDTO(
        recordCount: 2,
        firstWorkedOn: "2026-04-01",
        lastWorkedOn: "2026-04-10",
        workedDayCount: 2,
        averageIntervalDays: 9,
        photoAttachedRecordCount: 1,
        photoAttachmentRatePct: 50,
        weatherDistribution: [],
        averageTemperatureC: 18.5
    )

    private static func workTypeLabel(_ workType: WorkType) -> String {
        switch workType {
        case .planting: "심기"
        case .watering: "물주기"
        case .fertilizing: "비료"
        case .pestControl: "병해충"
        case .weeding: "잡초"
        case .pruning: "가지·순 정리"
        case .harvest: "수확"
        case .etc: "기타"
        }
    }
}

enum ReportRemoteStubResult<Value> {
    case success(Value)
    case failure(APIError)
}

@MainActor
final class StubReportRemoteDataSource: ReportRemoteDataSource {
    var pageResults: [ReportRemoteStubResult<FarmingWorkReportPageResponseDTO>]
    var detailResults: [ReportRemoteStubResult<FarmingWorkReportDetailResponseDTO>]
    var feedbackResults: [ReportRemoteStubResult<ReportFeedbackListResponseDTO>]
    var regenerateResults: [ReportRemoteStubResult<ReportFeedbackItemResponseDTO>]

    private(set) var queries: [ReportQuery] = []
    private(set) var regeneratedKeys: [WorkReportKey] = []

    init(
        pageResults: [ReportRemoteStubResult<FarmingWorkReportPageResponseDTO>] = [],
        detailResults: [ReportRemoteStubResult<FarmingWorkReportDetailResponseDTO>] = [],
        feedbackResults: [ReportRemoteStubResult<ReportFeedbackListResponseDTO>] = [],
        regenerateResults: [ReportRemoteStubResult<ReportFeedbackItemResponseDTO>] = []
    ) {
        self.pageResults = pageResults
        self.detailResults = detailResults
        self.feedbackResults = feedbackResults
        self.regenerateResults = regenerateResults
    }

    func fetchWorkItems(_ query: ReportQuery) async throws -> FarmingWorkReportPageResponseDTO {
        queries.append(query)
        return try resolve(&pageResults)
    }

    func fetchDetail(_ key: WorkReportKey) async throws -> FarmingWorkReportDetailResponseDTO {
        try resolve(&detailResults)
    }

    func fetchFeedback(reportId: UUID) async throws -> ReportFeedbackListResponseDTO {
        try resolve(&feedbackResults)
    }

    func regenerate(_ key: WorkReportKey) async throws -> ReportFeedbackItemResponseDTO {
        regeneratedKeys.append(key)
        return try resolve(&regenerateResults)
    }

    private func resolve<Value>(_ results: inout [ReportRemoteStubResult<Value>]) throws -> Value {
        guard !results.isEmpty else { throw APIError.network(URLError(.unknown)) }
        switch results.removeFirst() {
        case let .success(value): return value
        case let .failure(error): throw error
        }
    }
}
