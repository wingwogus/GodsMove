//
//  OnboardingEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import Foundation

/// Split out of `AuthEndpoint` (Features/Auth) — the backend groups this under the same `/api/v1/auth/*`
/// namespace as login/logout, but it's an onboarding-specific action, so it belongs to this feature's endpoints.
enum OnboardingEndpoint: Endpoint {
    case completeOnboarding(OnboardingCompleteRequestDTO)

    var path: String {
        switch self {
        case .completeOnboarding: "api/v1/auth/onboarding/complete"
        }
    }

    var method: HTTPMethod { .post }

    var requiresAuth: Bool { true }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .completeOnboarding(requestDTO):
            requestDTO
        }
    }
}
