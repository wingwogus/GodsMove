//
//  AuthRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

protocol AuthRepository: Sendable {
    func loginWithKakao(idToken: String, nonce: String, kakaoAccessToken: String?) async throws -> LoginResponseDTO
    func loginWithApple(
        identityToken: String,
        nonce: String,
        authorizationCode: String?,
        userIdentifier: String?,
        name: String?
    ) async throws -> LoginResponseDTO
    func loginWithNaver(accessToken: String) async throws -> LoginResponseDTO
    func logout() async throws
    func withdraw() async throws
}

struct RemoteAuthRepository: AuthRepository {
    let apiClient: APIClient
    let authTokenStore: AuthTokenStore

    func loginWithKakao(idToken: String, nonce: String, kakaoAccessToken: String?) async throws -> LoginResponseDTO {
        try await persistingTokens {
            try await apiClient.send(AuthEndpoint.kakaoLogin(idToken: idToken, nonce: nonce, kakaoAccessToken: kakaoAccessToken))
        }
    }

    func loginWithApple(
        identityToken: String,
        nonce: String,
        authorizationCode: String?,
        userIdentifier: String?,
        name: String?
    ) async throws -> LoginResponseDTO {
        try await persistingTokens {
            try await apiClient.send(AuthEndpoint.appleLogin(
                identityToken: identityToken,
                nonce: nonce,
                authorizationCode: authorizationCode,
                userIdentifier: userIdentifier,
                name: name
            ))
        }
    }

    func loginWithNaver(accessToken: String) async throws -> LoginResponseDTO {
        try await persistingTokens {
            try await apiClient.send(AuthEndpoint.naverLogin(accessToken: accessToken))
        }
    }

    func logout() async throws {
        let _: EmptyDTO = try await apiClient.send(AuthEndpoint.logout)
        await authTokenStore.clear()
        OnboardingDraftStore().clear()
    }

    /// Hard-deletes the member on the backend (DB cascade removes owned data, existing access
    /// tokens stop authenticating), then clears the local session the same way `logout()` does.
    func withdraw() async throws {
        let _: EmptyDTO = try await apiClient.send(MemberEndpoint.withdraw)
        await authTokenStore.clear()
        OnboardingDraftStore().clear()
    }

    /// Every provider's login call returns the same token-bearing shape — save to Keychain right where the
    /// tokens are received rather than pushing that responsibility onto each call site.
    private func persistingTokens(_ login: () async throws -> LoginResponseDTO) async throws -> LoginResponseDTO {
        let response = try await login()
        await authTokenStore.save(accessToken: response.accessToken, refreshToken: response.refreshToken)
        return response
    }
}
