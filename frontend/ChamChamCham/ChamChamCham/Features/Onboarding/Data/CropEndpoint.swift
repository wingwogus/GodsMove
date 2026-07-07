//
//  CropEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

/// Requires auth — `/api/v1/crops*` is not in the backend's `SecurityConfig.PUBLIC_ENDPOINTS` list.
enum CropEndpoint: Endpoint {
    case list
    case categories

    var path: String {
        switch self {
        case .list: "api/v1/crops"
        case .categories: "api/v1/crops/categories"
        }
    }

    var method: HTTPMethod { .get }
    var body: (any Encodable & Sendable)? { nil }
    var requiresAuth: Bool { true }
}
