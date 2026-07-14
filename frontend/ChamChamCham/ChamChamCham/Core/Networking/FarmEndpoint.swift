//
//  FarmEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Foundation

enum FarmEndpoint: Endpoint {
    case list
    case create(SaveFarmRequestDTO)
    case delete(UUID)

    var path: String {
        switch self {
        case .list, .create:
            "api/v1/farms"
        case let .delete(id):
            "api/v1/farms/\(id.uuidString)"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .list:
            .get
        case .create:
            .post
        case .delete:
            .delete
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case .list, .delete:
            nil
        case let .create(request):
            request
        }
    }

    var requiresAuth: Bool { true }
}
