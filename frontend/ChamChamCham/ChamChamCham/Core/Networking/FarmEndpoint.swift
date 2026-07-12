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

    var path: String { "api/v1/farms" }

    var method: HTTPMethod {
        switch self {
        case .list:
            .get
        case .create:
            .post
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case .list:
            nil
        case let .create(request):
            request
        }
    }

    var requiresAuth: Bool { true }
}
