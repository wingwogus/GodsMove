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
    private let supportEmail = "wingwogus@gmail.com"

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var viewModel: SettingsViewModel
    @State private var isConfirmingLogout = false
    @State private var isConfirmingWithdraw = false

    init(authRepository: any AuthRepository) {
        _viewModel = State(initialValue: SettingsViewModel(authRepository: authRepository))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                AppTopAppBar(
                    title: "설정",
                    isDetail: true,
                    leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
                )

                VStack(spacing: 0) {
                    linkRow(.privacyPolicy)
                    linkRow(.termsOfService)
                    linkRow(.locationTerms)
                    settingsRow("문의/고객센터") { openSupportEmail() }
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
            .navigationDestination(for: PolicyLink.self) { link in
                PolicyWebView(link: link)
            }
            .confirmationDialog("로그아웃할까요?", isPresented: $isConfirmingLogout, titleVisibility: .visible) {
                Button("로그아웃", role: .destructive) {
                    Task { await viewModel.logout(appState: appState) }
                }
                Button("취소", role: .cancel) {}
            }
            .confirmationDialog("정말 탈퇴하시겠어요?", isPresented: $isConfirmingWithdraw, titleVisibility: .visible) {
                Button("탈퇴할게요", role: .destructive) {
                    Task { await viewModel.withdraw(appState: appState) }
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text("탈퇴 후에는 계정을 복구할 수 없어요.")
            }
        }
    }

    private func openSupportEmail() {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = supportEmail
        components.queryItems = [URLQueryItem(name: "subject", value: "[참참참] 문의하기")]
        guard let url = components.url else { return }
        openURL(url)
    }

    private func rowLabel(_ title: String, isDestructive: Bool = false) -> some View {
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

    private func settingsRow(
        _ title: String,
        isDestructive: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            rowLabel(title, isDestructive: isDestructive)
        }
        .buttonStyle(.plain)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.Border.subtle).frame(height: 1)
        }
    }

    private func linkRow(_ link: PolicyLink) -> some View {
        NavigationLink(value: link) {
            rowLabel(link.title)
        }
        .buttonStyle(.plain)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.Border.subtle).frame(height: 1)
        }
    }
}
