//
//  MediaEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

enum MediaEndpoint: Endpoint {
    case uploadImage(UploadImageRequestDTO)

    var path: String {
        switch self {
        case .uploadImage: "api/v1/media/images"
        }
    }

    var method: HTTPMethod { .post }

    var requiresAuth: Bool { true }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .uploadImage(requestDTO):
            requestDTO
        }
    }
}
