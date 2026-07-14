//
//  PolicyEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// Query for `GET /api/v1/policies/recommendations`. Mirrors the backend query — `benefitCategory` is a free
/// string on the server (no enum in the schema), so a `nil` category means "전체" (no filter applied).
struct PolicyRecommendationQuery: Sendable, Equatable {
    var benefitCategory: PolicyCategory?
    var sort: String
    var cursor: String?
    var size: Int

    init(
        benefitCategory: PolicyCategory? = nil,
        sort: String = "RECOMMENDED",
        cursor: String? = nil,
        size: Int = 20
    ) {
        self.benefitCategory = benefitCategory
        self.sort = sort
        self.cursor = cursor
        self.size = size
    }
}

enum PolicyEndpoint: Endpoint {
    case recommendations(PolicyRecommendationQuery)
    case programLink(id: UUID)

    private static let base = "api/v1/policies"

    var path: String {
        switch self {
        case .recommendations:
            "\(Self.base)/recommendations"
        case let .programLink(id):
            "\(Self.base)/\(id.uuidString)"
        }
    }

    var method: HTTPMethod { .get }

    var body: (any Encodable & Sendable)? { nil }

    var requiresAuth: Bool { true }

    var queryItems: [URLQueryItem] {
        switch self {
        case let .recommendations(query):
            var items = [
                URLQueryItem(name: "size", value: String(query.size)),
                URLQueryItem(name: "sort", value: query.sort),
            ]
            if let category = query.benefitCategory {
                items.append(URLQueryItem(name: "benefitCategory", value: category.rawValue))
            }
            if let cursor = query.cursor {
                items.append(URLQueryItem(name: "cursor", value: cursor))
            }
            return items
        case .programLink:
            return []
        }
    }
}
