//
//  RecordEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

/// Search/paging parameters for `GET /api/v1/farming-records`. Mirrors the backend list query. Defaults match
/// the server (`size = 20`), so the common "first page, no filter" call is just `RecordQuery()`.
struct RecordQuery: Sendable, Equatable {
    var cropId: UUID?
    var workType: WorkType?
    var startDate: Date?
    var endDate: Date?
    var keyword: String?
    var cursor: String?
    var size: Int

    init(
        filter: RecordFilter = RecordFilter(),
        keyword: String? = nil,
        cursor: String? = nil,
        size: Int = 20
    ) {
        self.cropId = filter.cropId
        self.workType = filter.workType
        self.startDate = filter.startDate
        self.endDate = filter.endDate
        self.keyword = keyword
        self.cursor = cursor
        self.size = size
    }
}

/// Requires auth. `activeCrops` targets the members tree (`/members/me/farm-crops`); `pesticides`/`pests`/
/// `farmWeather` target other trees — they're the compose form's catalog/weather sources and only consumed
/// here, so they live in this endpoint for locality.
enum RecordEndpoint: Endpoint {
    case listRecords(RecordQuery)
    case recordDetail(id: UUID)
    case deleteRecord(id: UUID)
    case activeCrops
    case createRecord(SaveRecordRequestDTO)
    case pesticides(keyword: String?, cursor: String?, size: Int)
    case pests(pesticideId: UUID)
    case farmWeather(farmId: UUID)

    var path: String {
        switch self {
        case .listRecords, .createRecord:
            "api/v1/farming-records"
        case let .recordDetail(id), let .deleteRecord(id):
            "api/v1/farming-records/\(id.uuidString)"
        case .activeCrops:
            "api/v1/members/me/farm-crops"
        case .pesticides:
            "api/v1/pesticides"
        case let .pests(pesticideId):
            "api/v1/pesticides/\(pesticideId.uuidString)/pests"
        case let .farmWeather(farmId):
            "api/v1/farms/\(farmId.uuidString)/weather"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .createRecord:
            .post
        case .deleteRecord:
            .delete
        case .listRecords, .recordDetail, .activeCrops, .pesticides, .pests, .farmWeather:
            .get
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .createRecord(dto):
            dto
        default:
            nil
        }
    }

    var requiresAuth: Bool { true }

    var queryItems: [URLQueryItem] {
        switch self {
        case let .pesticides(keyword, cursor, size):
            var items = [URLQueryItem(name: "size", value: String(size))]
            if let keyword, !keyword.trimmingCharacters(in: .whitespaces).isEmpty {
                items.append(URLQueryItem(name: "keyword", value: keyword))
            }
            if let cursor {
                items.append(URLQueryItem(name: "cursor", value: cursor))
            }
            return items
        case let .listRecords(query):
            var items = [URLQueryItem(name: "size", value: String(query.size))]
            if let cropId = query.cropId {
                items.append(URLQueryItem(name: "cropId", value: cropId.uuidString))
            }
            if let workType = query.workType {
                items.append(URLQueryItem(name: "workType", value: workType.rawValue))
            }
            if let startDate = query.startDate {
                items.append(URLQueryItem(name: "startDate", value: Self.dateFormatter.string(from: startDate)))
            }
            if let endDate = query.endDate {
                items.append(URLQueryItem(name: "endDate", value: Self.dateFormatter.string(from: endDate)))
            }
            if let keyword = query.keyword, !keyword.trimmingCharacters(in: .whitespaces).isEmpty {
                items.append(URLQueryItem(name: "keyword", value: keyword))
            }
            if let cursor = query.cursor {
                items.append(URLQueryItem(name: "cursor", value: cursor))
            }
            return items
        case .recordDetail, .deleteRecord, .activeCrops, .createRecord, .pests, .farmWeather:
            return []
        }
    }

    /// The list endpoint's `startDate`/`endDate` are `format: date` (no time component).
    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
