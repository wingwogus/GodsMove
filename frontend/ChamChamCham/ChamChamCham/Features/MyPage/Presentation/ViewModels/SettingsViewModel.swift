//
//  SettingsViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Observation

/// Settings screen actions migrated from the former MyPage placeholder: logout and account
/// withdrawal.
@Observable
@MainActor
final class SettingsViewModel {
    var isSubmitting = false
    var message: String?

    @ObservationIgnored private let authRepository: any AuthRepository

    init(authRepository: any AuthRepository) {
        self.authRepository = authRepository
    }

    func logout(appState: AppState) async {
        guard !isSubmitting else { return }
        isSubmitting = true
        message = nil
        defer { isSubmitting = false }

        do {
            try await authRepository.logout()
            appState.isAuthenticated = false
            appState.isOnboarded = false
        } catch {
            message = "로그아웃에 실패했어요. 다시 시도해주세요."
        }
    }

    func withdraw(appState: AppState) async {
        guard !isSubmitting else { return }
        isSubmitting = true
        message = nil
        defer { isSubmitting = false }

        do {
            try await authRepository.withdraw()
            appState.isAuthenticated = false
            appState.isOnboarded = false
        } catch {
            message = "회원탈퇴에 실패했어요. 다시 시도해주세요."
        }
    }
}
