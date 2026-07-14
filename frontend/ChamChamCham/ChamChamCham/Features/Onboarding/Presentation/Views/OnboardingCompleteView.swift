//
//  OnboardingCompleteView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

/// Final onboarding decision screen. The server completion request is intentionally deferred until the user taps
/// "시작하기", because the Figma flow lets them add more farms from this screen before committing onboarding.
struct OnboardingCompleteView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @Environment(AppState.self) private var appState

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            successContent
            Spacer()
            bottomContent
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 44)
        .background(Color.Background.default)
    }

    private var successContent: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 108, height: 108)
                .foregroundStyle(Color.Object.primary)

            VStack(spacing: 4) {
                Text("가입 완료")
                    .font(AppTypography.headlineLargeEmphasized.font)
                    .foregroundStyle(Color.Text.default)

                Text("참참참에 오신 걸 환영합니다!")
                    .font(AppTypography.titleMedium.font)
                    .foregroundStyle(Color.Text.subtle)
            }
            .frame(maxWidth: .infinity)
            .multilineTextAlignment(.center)
        }
    }

    @ViewBuilder
    private var bottomContent: some View {
        switch viewModel.submissionState {
        case .idle:
            completionButtons(isDisabled: false)
        case .submitting:
            progressMessage("등록 중이에요...")
        case .uploadingPhoto:
            progressMessage("프로필 사진을 올리는 중이에요...")
        case .failed(let message):
            retryContent(message: message)
        case .photoUploadFailed(let message):
            photoRetryContent(message: message)
        }
    }

    private func progressMessage(_ message: String) -> some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(message)
                .font(AppTypography.bodyMedium.font)
                .foregroundStyle(Color.Text.subtle)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 100)
    }

    private func retryContent(message: String) -> some View {
        VStack(spacing: 12) {
            Text(message)
                .font(AppTypography.bodyMedium.font)
                .foregroundStyle(Color.Text.red)
                .multilineTextAlignment(.center)

            Button {
                Task { await viewModel.submit(appState: appState) }
            } label: {
                Text("다시 시도")
                    .font(AppTypography.bodyLarge.font)
                    .foregroundStyle(Color.Text.inverse)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.Object.bold)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
    }

    private func photoRetryContent(message: String) -> some View {
        VStack(spacing: 12) {
            Text(message)
                .font(AppTypography.bodyMedium.font)
                .foregroundStyle(Color.Text.red)
                .multilineTextAlignment(.center)

            HStack(spacing: 12) {
                Button {
                    Task { await viewModel.submit(appState: appState) }
                } label: {
                    secondaryButtonLabel("다시 시도")
                }

                Button {
                    Task { await viewModel.submitWithoutPhoto(appState: appState) }
                } label: {
                    primaryButtonLabel("사진 없이 계속")
                }
            }
        }
    }

    private func completionButtons(isDisabled: Bool) -> some View {
        HStack(spacing: 12) {
            Button {
                viewModel.addFarmFromCompletion()
            } label: {
                secondaryButtonLabel("재배지 추가하기")
            }
            .disabled(isDisabled)

            Button {
                Task { await viewModel.submit(appState: appState) }
            } label: {
                primaryButtonLabel("시작하기")
            }
            .disabled(isDisabled)
        }
        .frame(height: 56)
    }

    private func primaryButtonLabel(_ title: String) -> some View {
        Text(title)
            .font(AppTypography.bodyLarge.font)
            .foregroundStyle(Color.Text.inverse)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Color.Object.bold)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func secondaryButtonLabel(_ title: String) -> some View {
        Text(title)
            .font(AppTypography.bodyLarge.font)
            .foregroundStyle(Color.Text.subtle)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Color.Object.default)
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Color.Border.default, lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

#if DEBUG
#Preview {
    OnboardingCompleteView()
        .environment(OnboardingViewModel.preview())
        .environment(AppState())
}
#endif
