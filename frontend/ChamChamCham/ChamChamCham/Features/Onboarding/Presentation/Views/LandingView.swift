//
//  LandingView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct LandingView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @Environment(AuthViewModel.self) private var authViewModel
    @Environment(AppState.self) private var appState

    private var isLoggingIn: Bool { authViewModel.loginState == .loggingIn }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            AppIconView(source: .asset("app_logo"), size: 96, renderingMode: .original)

            Spacer().frame(height: Spacing.lg)

            Text("간편 로그인으로 시작하기")
                .appTypography(.bodyLargeEmphasized)
                .foregroundStyle(Color.Text.default)

            Spacer()

            VStack(spacing: Spacing.sm) {
                if case let .failed(message) = authViewModel.loginState {
                    Text(message)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                }

                SocialLoginButton(provider: .kakao, isDisabled: isLoggingIn) {
                    Task { await login(with: authViewModel.loginWithKakao) }
                }
                SocialLoginButton(provider: .naver, isDisabled: isLoggingIn) {
                    Task { await login(with: authViewModel.loginWithNaver) }
                }
                SocialLoginButton(provider: .apple, isDisabled: isLoggingIn) {
                    Task { await login(with: authViewModel.loginWithApple) }
                }

                Button("로그인 없이 둘러보기") {
                    appState.isGuest = true
                }
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
                .padding(.top, Spacing.xs)
                .disabled(isLoggingIn)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.xl)
    }

    private func login(with method: (AppState) async -> Void) async {
        await method(appState)
        guard appState.isAuthenticated else { return }
        if !appState.isOnboarded {
            viewModel.continueAfterAuthentication()
        }
        // else: appState now satisfies RootView's `isAuthenticated && isOnboarded` gate — it swaps to
        // MainTabView on its own, no further action needed here.
    }
}

/// Social login providers, ordered as designed: Kakao, Naver, Apple.
///
/// Colors follow each provider's official brand button guideline (Kakao yellow / Naver green /
/// Apple black) — these are one-off brand-mandated values, not Figma foundation tokens, so they're
/// kept local to this screen rather than added to `Color+App.swift`.
private enum SocialProvider {
    case kakao, naver, apple

    var title: String {
        switch self {
        case .kakao: return "카카오로 계속하기"
        case .naver: return "네이버로 계속하기"
        case .apple: return "Apple로 계속하기"
        }
    }

    var backgroundColor: Color {
        switch self {
        case .kakao: return Color(hex: 0xFEE500)
        case .naver: return Color(hex: 0x03C75A)
        case .apple: return .black
        }
    }

    var foregroundColor: Color {
        switch self {
        case .kakao: return Color(hex: 0x191919)
        case .naver, .apple: return .white
        }
    }

    // TODO: no official Kakao/Naver icon assets exist in Assets.xcassets yet — these SF Symbols
    // are stand-ins until the real brand glyphs are added under Assets.xcassets/icon.
    @ViewBuilder
    var glyph: some View {
        switch self {
        case .kakao:
            AppIconView(source: .system("message.fill"), size: 20)
        case .naver:
            Text("N")
                .font(.system(size: 15, weight: .heavy))
        case .apple:
            AppIconView(source: .system("apple.logo"), size: 18)
        }
    }
}

private struct SocialLoginButton: View {
    let provider: SocialProvider
    let isDisabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Text(provider.title)
                    .appTypography(.bodyLargeEmphasized)
                    .frame(maxWidth: .infinity, alignment: .center)

                HStack {
                    provider.glyph
                    Spacer()
                }
            }
            .foregroundStyle(provider.foregroundColor)
            .padding(.horizontal, Spacing.lg)
            .frame(height: 56)
            .frame(maxWidth: .infinity)
            .background(provider.backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .opacity(isDisabled ? 0.6 : 1)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
    }
}

#if DEBUG
#Preview {
    LandingView()
        .environment(OnboardingViewModel.preview())
        .environment(AuthViewModel(authRepository: PreviewAuthRepository(), memberProfileCache: PreviewMemberProfileCache()))
        .environment(AppState())
}
#endif
