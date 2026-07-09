//
//  NaverLoginBridge.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

enum NaverLoginError: Error {
    case sdkNotConfigured
    case canceledByUser
}

// Compiles against the SDK the moment it's added via Xcode > File > Add Package Dependencies
// (https://github.com/naver/naveridlogin-sdk-ios-swift, product: NidThirdPartyLogin) — no further
// code change needed here. Unlike the Kakao bridge, this API is verified directly against the SDK's
// current source (NidOAuth.swift/LoginResult.swift/AccessToken.swift on `main`), not guessed.
//
// Naver login is NOT OIDC — there's no ID token or nonce, just an OAuth access token the backend
// verifies server-side by calling Naver's own profile API with it.
#if canImport(NidThirdPartyLogin)
import NidThirdPartyLogin

enum NaverSDKBootstrap {
    /// `urlScheme` is this app's OWN custom URL scheme (not Naver's) — it must match both the
    /// `CFBundleURLSchemes` entry in Info-Additions.plist and the callback URL registered for this
    /// app in Naver Developers console.
    static func initialize() {
        NidOAuth.shared.initialize(
            appName: "참참참",
            clientId: Secrets.naverClientID,
            clientSecret: Secrets.naverClientSecret,
            urlScheme: "chamchamchamauth"
        )
    }

    @discardableResult
    static func handleOpenURL(_ url: URL) -> Bool {
        NidOAuth.shared.handleURL(url)
    }
}

@MainActor
enum NaverLoginBridge {
    static func login() async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            // NidOAuth's in-app-browser fallback uses ASWebAuthenticationSession under the hood,
            // whose completion handler can fire twice on cancellation — same risk as
            // KakaoLoginBridge. Guard against the repeat call.
            var didResume = false
            NidOAuth.shared.requestLogin { result in
                guard !didResume else { return }
                didResume = true

                switch result {
                case .success(let loginResult):
                    continuation.resume(returning: loginResult.accessToken.tokenString)
                case .failure(.clientError(.canceledByUser)):
                    continuation.resume(throwing: NaverLoginError.canceledByUser)
                case .failure(let error):
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
#else
enum NaverSDKBootstrap {
    static func initialize() {}

    @discardableResult
    static func handleOpenURL(_ url: URL) -> Bool { false }
}

enum NaverLoginBridge {
    static func login() async throws -> String {
        throw NaverLoginError.sdkNotConfigured
    }
}
#endif
