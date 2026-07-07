//
//  OnboardingCompleteView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

/// Submits the completed draft the moment this step appears — every prior step already gathered and validated its
/// own data, so there's nothing left for the user to confirm here. On success `AppState.isOnboarded` flips and
/// `RootView` swaps to `MainTabView` on its own; this view may only ever flash briefly.
struct OnboardingCompleteView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @Environment(AppState.self) private var appState

    var body: some View {
        VStack(spacing: Spacing.md) {
            switch viewModel.submissionState {
            case .idle, .submitting:
                ProgressView()
                Text("등록 중이에요...")
                    .font(.appBody)
                    .foregroundStyle(Color.appTextSecondary)
            case .uploadingPhoto:
                ProgressView()
                Text("프로필 사진을 올리는 중이에요...")
                    .font(.appBody)
                    .foregroundStyle(Color.appTextSecondary)
            case .failed(let message):
                Text(message)
                    .font(.appBody)
                    .foregroundStyle(Color.appTextSecondary)
                PrimaryButton(title: "다시 시도") {
                    Task { await viewModel.submit(appState: appState) }
                }
            case .photoUploadFailed(let message):
                Text(message)
                    .font(.appBody)
                    .foregroundStyle(Color.appTextSecondary)
                    .multilineTextAlignment(.center)
                PrimaryButton(title: "다시 시도") {
                    Task { await viewModel.submit(appState: appState) }
                }
                Button("사진 없이 계속하기") {
                    Task { await viewModel.submitWithoutPhoto(appState: appState) }
                }
                .font(.appBody)
                .foregroundStyle(Color.appTextSecondary)
            }
        }
        .padding(Spacing.lg)
        .task {
            guard viewModel.submissionState == .idle else { return }
            await viewModel.submit(appState: appState)
        }
    }
}

#if DEBUG
#Preview {
    OnboardingCompleteView()
        .environment(OnboardingViewModel.preview())
        .environment(AppState())
}
#endif
