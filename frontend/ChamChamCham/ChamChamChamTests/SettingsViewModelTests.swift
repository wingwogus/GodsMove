//
//  SettingsViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/13/26.
//

import Testing
@testable import ChamChamCham

@MainActor
@Suite("SettingsViewModel session actions")
struct SettingsViewModelTests {

    @Test("logout calls repository and routes back to auth flow")
    func logoutRoutesToAuthFlow() async {
        let authRepository = StubAuthRepository()
        let appState = AppState()
        appState.isAuthenticated = true
        appState.isOnboarded = true
        let viewModel = SettingsViewModel(authRepository: authRepository)

        await viewModel.logout(appState: appState)

        #expect(await authRepository.logoutCallCount() == 1)
        #expect(!appState.isAuthenticated)
        #expect(!appState.isOnboarded)
        #expect(viewModel.message == nil)
    }

    @Test("logout failure keeps session and exposes a message")
    func logoutFailureKeepsSession() async {
        let authRepository = StubAuthRepository(logoutError: MyPageTestError.unused)
        let appState = AppState()
        appState.isAuthenticated = true
        appState.isOnboarded = true
        let viewModel = SettingsViewModel(authRepository: authRepository)

        await viewModel.logout(appState: appState)

        #expect(await authRepository.logoutCallCount() == 1)
        #expect(appState.isAuthenticated)
        #expect(appState.isOnboarded)
        #expect(viewModel.message == "로그아웃에 실패했어요. 다시 시도해주세요.")
    }

    @Test("withdraw calls repository and routes back to auth flow")
    func withdrawRoutesToAuthFlow() async {
        let authRepository = StubAuthRepository()
        let appState = AppState()
        appState.isAuthenticated = true
        appState.isOnboarded = true
        let viewModel = SettingsViewModel(authRepository: authRepository)

        await viewModel.withdraw(appState: appState)

        #expect(await authRepository.withdrawCallCount() == 1)
        #expect(!appState.isAuthenticated)
        #expect(!appState.isOnboarded)
        #expect(viewModel.message == nil)
    }

    @Test("withdraw failure keeps session and exposes a message")
    func withdrawFailureKeepsSession() async {
        let authRepository = StubAuthRepository(withdrawError: MyPageTestError.unused)
        let appState = AppState()
        appState.isAuthenticated = true
        appState.isOnboarded = true
        let viewModel = SettingsViewModel(authRepository: authRepository)

        await viewModel.withdraw(appState: appState)

        #expect(await authRepository.withdrawCallCount() == 1)
        #expect(appState.isAuthenticated)
        #expect(appState.isOnboarded)
        #expect(viewModel.message == "회원탈퇴에 실패했어요. 다시 시도해주세요.")
    }
}
