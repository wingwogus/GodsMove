//
//  MyPageViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Observation

@Observable
@MainActor
final class MyPageViewModel {
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

    func withdraw() {
        message = "회원탈퇴 API가 아직 준비되지 않았어요."
    }
}
