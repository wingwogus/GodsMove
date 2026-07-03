//
//  KakaoLoginBridge.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

enum KakaoLoginError: Error {
    case sdkNotConfigured
    case missingIDToken
}

// Compiles against the SDK the moment it's added via Xcode > File > Add Package Dependencies
// (https://github.com/kakao/kakao-ios-sdk, products: KakaoSDKCommon, KakaoSDKAuth, KakaoSDKUser) —
// no further code change needed here. Until then the #else stub below keeps the app building.
// API verified directly against the SDK's current source (UserApi.swift/AuthController.swift/
// KakaoSDKCommon.swift on `master`), not guessed.
#if canImport(KakaoSDKAuth) && canImport(KakaoSDKCommon) && canImport(KakaoSDKUser)
import KakaoSDKAuth
import KakaoSDKCommon
import KakaoSDKUser

@MainActor
enum KakaoSDKBootstrap {
    static func initialize() {
        KakaoSDK.initSDK(appKey: Secrets.kakaoNativeAppKey)
    }

    @discardableResult
    static func handleOpenURL(_ url: URL) -> Bool {
        guard AuthApi.isKakaoTalkLoginUrl(url) else { return false }
        return AuthController.handleOpenUrl(url: url)
    }
}

@MainActor
enum KakaoLoginBridge {
    /// Returns `(idToken, kakaoAccessToken)` — OIDC must be enabled for the app in Kakao Developers console
    /// or `idToken` stays nil and this throws `.missingIDToken`.
    static func login(nonce: String) async throws -> (idToken: String, accessToken: String?) {
        try await withCheckedThrowingContinuation { continuation in
            let completion: (OAuthToken?, Error?) -> Void = { token, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let idToken = token?.idToken else {
                    continuation.resume(throwing: KakaoLoginError.missingIDToken)
                    return
                }
                continuation.resume(returning: (idToken, token?.accessToken))
            }

            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk(nonce: nonce, completion: completion)
            } else {
                UserApi.shared.loginWithKakaoAccount(nonce: nonce, completion: completion)
            }
        }
    }
}
#else
@MainActor
enum KakaoSDKBootstrap {
    static func initialize() {}

    @discardableResult
    static func handleOpenURL(_ url: URL) -> Bool { false }
}

enum KakaoLoginBridge {
    static func login(nonce: String) async throws -> (idToken: String, accessToken: String?) {
        throw KakaoLoginError.sdkNotConfigured
    }
}
#endif
