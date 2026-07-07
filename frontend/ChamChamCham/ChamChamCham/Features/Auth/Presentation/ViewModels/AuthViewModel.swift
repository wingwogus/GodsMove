//
//  AuthViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import AuthenticationServices
import Observation

@Observable
@MainActor
final class AuthViewModel {
    enum LoginState: Equatable {
        case idle
        case loggingIn
        case failed(String)
    }

    var loginState: LoginState = .idle

    private let authRepository: AuthRepository
    private let memberProfileCache: MemberProfileCache
    private let appleSignInCoordinator = AppleSignInCoordinator()

    init(authRepository: AuthRepository, memberProfileCache: MemberProfileCache) {
        self.authRepository = authRepository
        self.memberProfileCache = memberProfileCache
    }

    func loginWithApple(appState: AppState) async {
        guard loginState != .loggingIn else { return }
        loginState = .loggingIn
        do {
            let rawNonce = NonceGenerator.generate()
            let hashedNonce = NonceGenerator.sha256Hex(rawNonce)
            let credential = try await appleSignInCoordinator.signIn(hashedNonce: hashedNonce)

            guard let tokenData = credential.identityToken,
                  let identityToken = String(data: tokenData, encoding: .utf8) else {
                throw AppleSignInError.missingIdentityToken
            }
            let authorizationCode = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }

            // Raw (unhashed) nonce — the backend hashes it itself and compares against the ID token's claim.
            // Sending the already-hashed value here would make every Apple login fail nonce verification.
            let response = try await authRepository.loginWithApple(
                identityToken: identityToken,
                nonce: rawNonce,
                authorizationCode: authorizationCode,
                userIdentifier: credential.user
            )
            handleLoginSuccess(response, appState: appState)
        } catch let authError as ASAuthorizationError where authError.code == .canceled {
            loginState = .idle
        } catch {
            loginState = .failed("Apple 로그인에 실패했어요. 잠시 후 다시 시도해주세요.")
        }
    }

    func loginWithKakao(appState: AppState) async {
        guard loginState != .loggingIn else { return }
        loginState = .loggingIn
        do {
            let rawNonce = NonceGenerator.generate()
            let (idToken, kakaoAccessToken) = try await KakaoLoginBridge.login(nonce: rawNonce)
            let response = try await authRepository.loginWithKakao(
                idToken: idToken,
                nonce: rawNonce,
                kakaoAccessToken: kakaoAccessToken
            )
            handleLoginSuccess(response, appState: appState)
        } catch is CancellationError {
            loginState = .idle
        } catch {
            loginState = .failed("카카오 로그인에 실패했어요. 잠시 후 다시 시도해주세요.")
        }
    }

    func loginWithNaver(appState: AppState) async {
        guard loginState != .loggingIn else { return }
        loginState = .loggingIn
        do {
            let accessToken = try await NaverLoginBridge.login()
            let response = try await authRepository.loginWithNaver(accessToken: accessToken)
            handleLoginSuccess(response, appState: appState)
        } catch NaverLoginError.canceledByUser {
            loginState = .idle
        } catch {
            loginState = .failed("네이버 로그인에 실패했어요. 잠시 후 다시 시도해주세요.")
        }
    }

    private func handleLoginSuccess(_ response: LoginResponseDTO, appState: AppState) {
        memberProfileCache.save(member: response.member, onboarding: response.onboarding)
        appState.isAuthenticated = true
        appState.isOnboarded = response.onboarding.status == .complete
        loginState = .idle
    }
}
