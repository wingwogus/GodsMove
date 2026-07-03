//
//  LandingView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftData
import SwiftUI

struct LandingView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @Environment(AuthViewModel.self) private var authViewModel
    @Environment(AppState.self) private var appState
    @Environment(\.modelContext) private var modelContext

    private var isLoggingIn: Bool { authViewModel.loginState == .loggingIn }

    var body: some View {
        VStack(spacing: Spacing.md) {
            if case let .failed(message) = authViewModel.loginState {
                Text(message)
                    .font(.appCaption)
                    .foregroundStyle(.red)
            }

            PrimaryButton(title: "카카오로 시작하기") {
                Task { await login(with: authViewModel.loginWithKakao) }
            }
            .disabled(isLoggingIn)
            PrimaryButton(title: "네이버로 시작하기") {
                Task { await login(with: authViewModel.loginWithNaver) }
            }
            .disabled(isLoggingIn)
            PrimaryButton(title: isLoggingIn ? "로그인 중..." : "Apple로 시작하기") {
                Task { await login(with: authViewModel.loginWithApple) }
            }
            .disabled(isLoggingIn)
        }
        .padding(Spacing.lg)
    }

    private func login(with method: (AppState, ModelContext) async -> Void) async {
        await method(appState, modelContext)
        guard appState.isAuthenticated else { return }
        if !appState.isOnboarded {
            viewModel.jump(to: .basicProfile)
        }
        // else: appState now satisfies RootView's `isAuthenticated && isOnboarded` gate — it swaps to
        // MainTabView on its own, no further action needed here.
    }
}

#Preview {
    LandingView()
        .environment(OnboardingViewModel.preview())
        .environment(AuthViewModel(authRepository: PreviewAuthRepository()))
        .environment(AppState())
}
