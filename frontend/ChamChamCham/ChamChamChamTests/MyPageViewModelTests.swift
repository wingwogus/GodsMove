//
//  MyPageViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Testing
@testable import ChamChamCham

@MainActor
@Suite("MyPageViewModel session actions")
struct MyPageViewModelTests {

    @Test("logout calls repository and routes back to auth flow")
    func logoutRoutesToAuthFlow() async {
        let authRepository = FakeMyPageAuthRepository()
        let appState = AppState()
        appState.isAuthenticated = true
        appState.isOnboarded = true
        let viewModel = MyPageViewModel(authRepository: authRepository)

        await viewModel.logout(appState: appState)

        #expect(await authRepository.logoutCallCount() == 1)
        #expect(!appState.isAuthenticated)
        #expect(!appState.isOnboarded)
        #expect(viewModel.message == nil)
    }

    @Test("logout failure keeps current session and exposes a message")
    func logoutFailureKeepsSession() async {
        let authRepository = FakeMyPageAuthRepository(logoutError: MyPageTestError.expected)
        let appState = AppState()
        appState.isAuthenticated = true
        appState.isOnboarded = true
        let viewModel = MyPageViewModel(authRepository: authRepository)

        await viewModel.logout(appState: appState)

        #expect(await authRepository.logoutCallCount() == 1)
        #expect(appState.isAuthenticated)
        #expect(appState.isOnboarded)
        #expect(viewModel.message == "로그아웃에 실패했어요. 다시 시도해주세요.")
    }

    @Test("withdrawal button reports that the backend contract is missing")
    func withdrawalReportsMissingBackendContract() async {
        let authRepository = FakeMyPageAuthRepository()
        let viewModel = MyPageViewModel(authRepository: authRepository)

        viewModel.withdraw()

        #expect(await authRepository.logoutCallCount() == 0)
        #expect(viewModel.message == "회원탈퇴 API가 아직 준비되지 않았어요.")
    }
}

private enum MyPageTestError: Error {
    case expected
    case unused
}

private actor FakeMyPageAuthRepository: AuthRepository {
    private var logoutCalls = 0
    private let logoutError: Error?

    init(logoutError: Error? = nil) {
        self.logoutError = logoutError
    }

    func loginWithKakao(idToken: String, nonce: String, kakaoAccessToken: String?) async throws -> LoginResponseDTO {
        throw MyPageTestError.unused
    }

    func loginWithApple(
        identityToken: String,
        nonce: String,
        authorizationCode: String?,
        userIdentifier: String?
    ) async throws -> LoginResponseDTO {
        throw MyPageTestError.unused
    }

    func loginWithNaver(accessToken: String) async throws -> LoginResponseDTO {
        throw MyPageTestError.unused
    }

    func logout() async throws {
        logoutCalls += 1
        if let logoutError {
            throw logoutError
        }
    }

    func logoutCallCount() -> Int {
        logoutCalls
    }
}
