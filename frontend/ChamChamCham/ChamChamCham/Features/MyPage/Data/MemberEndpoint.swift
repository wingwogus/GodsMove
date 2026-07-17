//
//  MemberEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

enum MemberEndpoint: Endpoint {
    case myProfile
    case publicProfile(UUID)
    case updateMyProfile(UpdateMyProfileRequestDTO)
    case withdraw

    var path: String {
        switch self {
        case .myProfile, .withdraw:
            "api/v1/members/me"
        case let .publicProfile(memberId):
            "api/v1/members/\(memberId.uuidString)/profile"
        case .updateMyProfile:
            "api/v1/members/me/profile"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .myProfile, .publicProfile:
            .get
        case .updateMyProfile:
            .put
        case .withdraw:
            .delete
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .updateMyProfile(request):
            request
        case .myProfile, .publicProfile, .withdraw:
            nil
        }
    }

    var requiresAuth: Bool { true }
}
