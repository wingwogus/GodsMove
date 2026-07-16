//
//  FarmingWorkReportDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct FarmingWorkReportPageResponseDTO: Codable, Sendable {
    let items: [FarmingWorkReportItemResponseDTO]
    let nextCursor: String?

    func toDomain() -> FarmingWorkReportPage {
        FarmingWorkReportPage(
            items: items.compactMap { $0.toDomain() },
            nextCursor: nextCursor
        )
    }
}

struct FarmingWorkReportItemResponseDTO: Codable, Sendable {
    let reportId: UUID
    let status: String
    let farmId: UUID
    let farmName: String
    let cropId: UUID
    let cropName: String
    let startsAt: String
    let endsAt: String?
    let workType: String
    let workTypeLabel: String
    let recordCount: Int
    let lastWorkedOn: String?
    let thumbnailUrl: String?

    func toDomain() -> FarmingWorkReportSummary? {
        guard let workType = WorkType(rawValue: workType) else { return nil }
        return FarmingWorkReportSummary(
            key: WorkReportKey(reportId: reportId, workType: workType),
            status: ReportCycleStatus(rawValue: status),
            farmId: farmId,
            farmName: farmName,
            cropId: cropId,
            cropName: cropName,
            startsAt: ReportDateParser.requiredLocalDateTime(from: startsAt),
            endsAt: ReportDateParser.localDateTime(from: endsAt),
            workTypeLabel: workTypeLabel,
            recordCount: recordCount,
            lastWorkedOn: ReportDateParser.localDate(from: lastWorkedOn),
            thumbnailUrl: thumbnailUrl
        )
    }
}

struct FarmingWorkReportDetailResponseDTO: Codable, Sendable {
    let reportId: UUID
    let status: String
    let workType: String
    let workTypeLabel: String
    let farmId: UUID
    let farmName: String
    let cropId: UUID
    let cropName: String
    let startsAt: String
    let endsAt: String?
    let statistics: FarmingWorkReportStatisticsResponseDTO
    let feedback: FarmingWorkReportEmbeddedFeedbackResponseDTO?

    func toDomain() throws -> FarmingWorkReportDetail {
        guard let mappedWorkType = WorkType(rawValue: workType) else {
            throw ReportMappingError.unsupportedWorkType(workType)
        }
        return FarmingWorkReportDetail(
            key: WorkReportKey(reportId: reportId, workType: mappedWorkType),
            status: ReportCycleStatus(rawValue: status),
            workTypeLabel: workTypeLabel,
            farmId: farmId,
            farmName: farmName,
            cropId: cropId,
            cropName: cropName,
            startsAt: ReportDateParser.requiredLocalDateTime(from: startsAt),
            endsAt: ReportDateParser.localDateTime(from: endsAt),
            statistics: statistics.toDomain(),
            feedback: feedback?.toDomain()
        )
    }
}

struct FarmingWorkReportStatisticsResponseDTO: Codable, Sendable {
    let common: ReportCommonStatisticsResponseDTO
    let planting: PlantingReportStatisticsResponseDTO?
    let watering: WateringReportStatisticsResponseDTO?
    let fertilizing: FertilizingReportStatisticsResponseDTO?
    let pestControl: PestControlReportStatisticsResponseDTO?
    let weeding: WeedingReportStatisticsResponseDTO?
    let harvest: HarvestReportStatisticsResponseDTO?

    func toDomain() -> FarmingWorkReportStatistics {
        FarmingWorkReportStatistics(
            common: common.toDomain(),
            planting: planting?.toDomain(),
            watering: watering?.toDomain(),
            fertilizing: fertilizing?.toDomain(),
            pestControl: pestControl?.toDomain(),
            weeding: weeding?.toDomain(),
            harvest: harvest?.toDomain()
        )
    }
}

struct FarmingWorkReportEmbeddedFeedbackResponseDTO: Codable, Sendable {
    let status: String
    let content: ReportFeedbackContentResponseDTO?

    func toDomain() -> ReportFeedbackStatus {
        ReportFeedbackStatus(
            state: ReportFeedbackState(rawValue: status),
            content: content?.toDomain()
        )
    }
}

struct ReportFeedbackContentResponseDTO: Codable, Sendable {
    let summary: String
    let comparisons: [ReportFeedbackBulletResponseDTO]
    let strengths: [ReportFeedbackBulletResponseDTO]
    let improvements: [ReportFeedbackBulletResponseDTO]
    let nextActions: [ReportFeedbackBulletResponseDTO]

    func toDomain() -> ReportFeedbackContent {
        ReportFeedbackContent(
            summary: summary,
            comparisons: comparisons.map(\.text),
            strengths: strengths.map(\.text),
            improvements: improvements.map(\.text),
            nextActions: nextActions.map(\.text)
        )
    }
}

struct ReportFeedbackBulletResponseDTO: Codable, Sendable {
    let text: String
}

struct ReportFeedbackListResponseDTO: Codable, Sendable {
    let reportId: UUID
    let feedbacks: [ReportFeedbackItemResponseDTO]

    func toDomain() -> ReportFeedbackList {
        ReportFeedbackList(
            reportId: reportId,
            feedbacks: feedbacks.compactMap { $0.toDomain() }
        )
    }
}

struct ReportFeedbackItemResponseDTO: Codable, Sendable {
    let feedbackId: UUID
    let workType: String
    let status: String
    let inputPrepared: Bool
    let failureCode: String?
    let feedback: ReportFeedbackContentResponseDTO?
    let createdAt: String
    let updatedAt: String

    func toDomain() -> ReportFeedbackItem? {
        guard let workType = WorkType(rawValue: workType) else { return nil }
        return ReportFeedbackItem(
            id: feedbackId,
            workType: workType,
            state: ReportFeedbackState(rawValue: status),
            inputPrepared: inputPrepared,
            failureCode: failureCode,
            content: feedback?.toDomain(),
            createdAt: ReportDateParser.requiredLocalDateTime(from: createdAt),
            updatedAt: ReportDateParser.requiredLocalDateTime(from: updatedAt)
        )
    }
}
