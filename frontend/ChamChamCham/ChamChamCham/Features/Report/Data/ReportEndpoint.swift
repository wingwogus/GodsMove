//
//  ReportEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ReportQuery: Equatable, Sendable {
    var farmId: UUID?
    var cropId: UUID?
    var workType: WorkType?
    var cursor: String?
    var size = 20
}

enum ReportEndpoint: Endpoint {
    case workItems(ReportQuery)
    case workDetail(reportId: UUID, workType: WorkType)
    case feedback(reportId: UUID)
    case regenerate(reportId: UUID, workType: WorkType)

    var path: String {
        switch self {
        case .workItems:
            "api/v1/farming-reports/work-items"
        case let .workDetail(reportId, workType):
            "api/v1/farming-reports/\(reportId.uuidString)/work-types/\(workType.rawValue)"
        case let .feedback(reportId):
            "api/v1/farming-reports/\(reportId.uuidString)/feedback"
        case let .regenerate(reportId, workType):
            "api/v1/farming-reports/\(reportId.uuidString)/feedback/\(workType.rawValue)/regenerate"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .workItems, .workDetail, .feedback:
            .get
        case .regenerate:
            .post
        }
    }

    var body: (any Encodable & Sendable)? { nil }

    var requiresAuth: Bool { true }

    var queryItems: [URLQueryItem] {
        guard case let .workItems(query) = self else { return [] }

        var items = [URLQueryItem(name: "size", value: String(query.size))]
        if let farmId = query.farmId {
            items.append(URLQueryItem(name: "farmId", value: farmId.uuidString))
        }
        if let cropId = query.cropId {
            items.append(URLQueryItem(name: "cropId", value: cropId.uuidString))
        }
        if let workType = query.workType {
            items.append(URLQueryItem(name: "workType", value: workType.rawValue))
        }
        if let cursor = query.cursor, !cursor.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            items.append(URLQueryItem(name: "cursor", value: cursor))
        }
        return items
    }
}
