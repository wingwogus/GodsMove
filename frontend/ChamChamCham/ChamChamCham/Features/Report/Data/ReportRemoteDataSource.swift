//
//  ReportRemoteDataSource.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

@MainActor
protocol ReportRemoteDataSource: Sendable {
    func fetchWorkItems(_ query: ReportQuery) async throws -> FarmingWorkReportPageResponseDTO
    func fetchDetail(_ key: WorkReportKey) async throws -> FarmingWorkReportDetailResponseDTO
    func fetchFeedback(reportId: UUID) async throws -> ReportFeedbackListResponseDTO
    func regenerate(_ key: WorkReportKey) async throws -> ReportFeedbackItemResponseDTO
}

@MainActor
final class LiveReportRemoteDataSource: ReportRemoteDataSource {
    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func fetchWorkItems(_ query: ReportQuery) async throws -> FarmingWorkReportPageResponseDTO {
        try await apiClient.send(ReportEndpoint.workItems(query))
    }

    func fetchDetail(_ key: WorkReportKey) async throws -> FarmingWorkReportDetailResponseDTO {
        try await apiClient.send(ReportEndpoint.workDetail(
            reportId: key.reportId,
            workType: key.workType
        ))
    }

    func fetchFeedback(reportId: UUID) async throws -> ReportFeedbackListResponseDTO {
        try await apiClient.send(ReportEndpoint.feedback(reportId: reportId))
    }

    func regenerate(_ key: WorkReportKey) async throws -> ReportFeedbackItemResponseDTO {
        try await apiClient.send(ReportEndpoint.regenerate(
            reportId: key.reportId,
            workType: key.workType
        ))
    }
}
