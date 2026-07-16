//
//  FarmingWorkReportModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

enum ReportCycleStatus: Hashable, Sendable {
    case active
    case completed
    case unsupported(String)

    init(rawValue: String) {
        switch rawValue {
        case "ACTIVE": self = .active
        case "COMPLETED": self = .completed
        default: self = .unsupported(rawValue)
        }
    }
}

enum ReportFeedbackState: Hashable, Sendable {
    case pending
    case ready
    case failed
    case stale
    case unsupported(String)

    init(rawValue: String) {
        switch rawValue {
        case "PENDING": self = .pending
        case "READY": self = .ready
        case "FAILED": self = .failed
        case "STALE": self = .stale
        default: self = .unsupported(rawValue)
        }
    }
}

struct WorkReportKey: Identifiable, Hashable, Sendable {
    var id: Self { self }

    let reportId: UUID
    let workType: WorkType
}

struct FarmingWorkReportSummary: Identifiable, Hashable, Sendable {
    var id: WorkReportKey { key }

    let key: WorkReportKey
    let status: ReportCycleStatus
    let farmId: UUID
    let farmName: String
    let cropId: UUID
    let cropName: String
    let startsAt: Date
    let endsAt: Date?
    let workTypeLabel: String
    let recordCount: Int
    let lastWorkedOn: Date?
    let thumbnailUrl: String?
}

struct FarmingWorkReportPage: Hashable, Sendable {
    let items: [FarmingWorkReportSummary]
    let nextCursor: String?
}

struct FarmingWorkReportDetail: Identifiable, Hashable, Sendable {
    var id: WorkReportKey { key }

    let key: WorkReportKey
    let status: ReportCycleStatus
    let workTypeLabel: String
    let farmId: UUID
    let farmName: String
    let cropId: UUID
    let cropName: String
    let startsAt: Date
    let endsAt: Date?
    let statistics: FarmingWorkReportStatistics
    let feedback: ReportFeedbackStatus?
}

struct ReportFeedbackContent: Hashable, Sendable {
    let summary: String
    let comparisons: [String]
    let strengths: [String]
    let improvements: [String]
    let nextActions: [String]
}

struct ReportFeedbackStatus: Hashable, Sendable {
    let state: ReportFeedbackState
    let content: ReportFeedbackContent?
}

struct ReportFeedbackItem: Identifiable, Hashable, Sendable {
    let id: UUID
    let workType: WorkType
    let state: ReportFeedbackState
    let inputPrepared: Bool
    let failureCode: String?
    let content: ReportFeedbackContent?
    let createdAt: Date
    let updatedAt: Date
}

struct ReportFeedbackList: Hashable, Sendable {
    let reportId: UUID
    let feedbacks: [ReportFeedbackItem]
}

struct ReportFilter: Equatable, Sendable {
    var farmId: UUID?
    var cropId: UUID?
    var workType: WorkType?

    var isEmpty: Bool {
        farmId == nil && cropId == nil && workType == nil
    }
}

struct ReportCropFilterOption: Identifiable, Hashable, Sendable {
    let id: UUID
    let farmId: UUID
    let name: String
}

struct ReportFarmFilterOption: Identifiable, Hashable, Sendable {
    let id: UUID
    let name: String
    let crops: [ReportCropFilterOption]

    init(farm: FarmWithCrops) {
        id = farm.farmId
        name = farm.farmName
        crops = farm.crops.map {
            ReportCropFilterOption(id: $0.id, farmId: farm.farmId, name: $0.name)
        }
    }
}

enum ReportMappingError: Error, Equatable, Sendable {
    case unsupportedWorkType(String)
}
