//
//  AuthEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

/// No `.reissue` case here on purpose — `TokenRefreshCoordinator` calls `/reissue` directly with its own
/// `URLSession`, independent of `APIClient`, so a 401 mid-reissue can never recurse into itself.
enum AuthEndpoint: Endpoint {
    case kakaoLogin(idToken: String, nonce: String, kakaoAccessToken: String?)
    case appleLogin(identityToken: String, nonce: String, authorizationCode: String?, userIdentifier: String?)
    case naverLogin(accessToken: String)
    case logout

    var path: String {
        switch self {
        case .kakaoLogin: "api/v1/auth/kakao/login"
        case .appleLogin: "api/v1/auth/apple/login"
        case .naverLogin: "api/v1/auth/naver/login"
        case .logout: "api/v1/auth/logout"
        }
    }

    var method: HTTPMethod { .post }

    var requiresAuth: Bool {
        switch self {
        case .logout: true
        case .kakaoLogin, .appleLogin, .naverLogin: false
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .kakaoLogin(idToken, nonce, kakaoAccessToken):
            KakaoLoginRequestBody(idToken: idToken, nonce: nonce, kakaoAccessToken: kakaoAccessToken)
        case let .appleLogin(identityToken, nonce, authorizationCode, userIdentifier):
            AppleLoginRequestBody(
                identityToken: identityToken,
                nonce: nonce,
                authorizationCode: authorizationCode,
                userIdentifier: userIdentifier
            )
        case let .naverLogin(accessToken):
            NaverLoginRequestBody(accessToken: accessToken)
        case .logout:
            nil
        }
    }
}

private struct KakaoLoginRequestBody: Encodable, Sendable {
    let idToken: String
    let nonce: String
    let kakaoAccessToken: String?
}

private struct AppleLoginRequestBody: Encodable, Sendable {
    let identityToken: String
    let nonce: String
    let authorizationCode: String?
    let userIdentifier: String?
}

private struct NaverLoginRequestBody: Encodable, Sendable {
    let accessToken: String
}
