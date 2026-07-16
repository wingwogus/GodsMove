//
//  SettingsView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 설정 화면. Presented from the profile main settings icon. Houses logout and account withdrawal
/// (migrated from the former MyPage placeholder). No Figma frame yet — built from the design system.
struct SettingsView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: SettingsViewModel
    @State private var isConfirmingLogout = false
    @State private var isConfirmingWithdraw = false

    init(authRepository: any AuthRepository) {
        _viewModel = State(initialValue: SettingsViewModel(authRepository: authRepository))
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "설정",
                isDetail: true,
                leading: .init(.asset("chevron_backward")) { dismiss() }
            )

            VStack(spacing: 0) {
                settingsRow("로그아웃") { isConfirmingLogout = true }
                settingsRow("회원탈퇴", isDestructive: true) { isConfirmingWithdraw = true }
            }

            if let message = viewModel.message {
                Text(message)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
                    .padding(.horizontal, Spacing.lg - Spacing.xs)
                    .padding(.top, Spacing.md)
            }

            Spacer()
        }
        .background(Color.Background.default)
        .disabled(viewModel.isSubmitting)
        .confirmationDialog("로그아웃할까요?", isPresented: $isConfirmingLogout, titleVisibility: .visible) {
            Button("로그아웃", role: .destructive) {
                Task { await viewModel.logout(appState: appState) }
            }
            Button("취소", role: .cancel) {}
        }
        .confirmationDialog("회원탈퇴할까요?", isPresented: $isConfirmingWithdraw, titleVisibility: .visible) {
            Button("회원탈퇴", role: .destructive) { viewModel.withdraw() }
            Button("취소", role: .cancel) {}
        }
    }

    private func settingsRow(
        _ title: String,
        isDestructive: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(isDestructive ? Color.Text.red : Color.Text.default)
                Spacer()
                AppIconView(source: .asset("chevron_forward"), size: 24)
                    .foregroundStyle(Color.Icon.subtle)
            }
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .frame(height: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.Border.subtle).frame(height: 1)
        }
    }
}
