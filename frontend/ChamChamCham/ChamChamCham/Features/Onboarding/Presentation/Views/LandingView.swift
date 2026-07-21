//
//  LandingView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import AuthenticationServices
import SwiftUI

struct LandingView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @Environment(AuthViewModel.self) private var authViewModel
    @Environment(AppState.self) private var appState
    @State private var appleNonce = ""
    @State private var presentedPolicyLink: PolicyLink?

    private var isLoggingIn: Bool { authViewModel.loginState == .loggingIn }

    /// Shared sizing so Kakao / Naver / Apple buttons form a visually uniform stack.
    fileprivate static let buttonHeight: CGFloat = 56
    fileprivate static let buttonCornerRadius: CGFloat = 12

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            AppIconView(source: .asset("app_logo"), size: 120, renderingMode: .original)

            Spacer()

            VStack(spacing: Spacing.sm) {
                Text("간편 로그인으로 시작하기")
                    .appTypography(.bodyLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .padding(.bottom, Spacing.sm)

                if case let .failed(message) = authViewModel.loginState {
                    Text(message)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                }

                // Apple requires its own rendered mark or the official `SignInWithAppleButton`. Placed
                // first: it needs no account we can hand a reviewer, unlike Kakao/Naver below, which
                // require a Korea-phone-verified account.
                SignInWithAppleButton(.signIn) { request in
                    let rawNonce = NonceGenerator.generate()
                    appleNonce = rawNonce
                    request.requestedScopes = [.fullName, .email]
                    request.nonce = NonceGenerator.sha256Hex(rawNonce)
                } onCompletion: { result in
                    Task {
                        await authViewModel.loginWithApple(result: result, rawNonce: appleNonce, appState: appState)
                        // Apple's official button can't route through `login(with:)` (its completion has a
                        // different signature), so advance onboarding here just like the Kakao/Naver path —
                        // otherwise a new (not-yet-onboarded) account authenticates but stays stuck on Landing.
                        advanceAfterAuthentication()
                    }
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: Self.buttonHeight)
                .clipShape(RoundedRectangle(cornerRadius: Self.buttonCornerRadius))
                .disabled(isLoggingIn)
                .opacity(isLoggingIn ? 0.6 : 1)

                // Kakao/Naver ship official pre-rendered brand buttons (logo + label baked in), so we
                // render the asset directly instead of composing our own. Height is pinned to match
                // the Apple button above; corners clipped to the same radius for a uniform stack.
                BrandLoginButton(asset: "kakao_login_large_wide", isDisabled: isLoggingIn) {
                    Task { await login(with: authViewModel.loginWithKakao) }
                }
                BrandLoginButton(asset: "NAVER_login_Light_KR_green_wide_H56", isDisabled: isLoggingIn) {
                    Task { await login(with: authViewModel.loginWithNaver) }
                }

                Button("로그인 없이 둘러보기") {
                    appState.isGuest = true
                }
                .appTypography(.labelMediumEmphasized)
                .foregroundStyle(Color.Text.subtle)
                .underline()
                .padding(.top, Spacing.xs)
                .disabled(isLoggingIn)

                policyLinksRow
                    .padding(.top, Spacing.md)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .sheet(item: $presentedPolicyLink) { link in
            NavigationStack {
                PolicyWebView(link: link)
            }
        }
    }

    private var policyLinksRow: some View {
        HStack(spacing: Spacing.xs) {
            policyLinkButton(.termsOfService)
            Text("·")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
            policyLinkButton(.privacyPolicy)
        }
    }

    private func policyLinkButton(_ link: PolicyLink) -> some View {
        Button(link.title) {
            presentedPolicyLink = link
        }
        .appTypography(.labelMedium)
        .foregroundStyle(Color.Text.muted)
    }

    private func login(with method: (AppState) async -> Void) async {
        await method(appState)
        advanceAfterAuthentication()
    }

    /// Post-login navigation shared by every provider. A newly authenticated account that still needs
    /// onboarding is pushed into the flow; an already-onboarded account satisfies RootView's
    /// `isAuthenticated && isOnboarded` gate, which swaps to MainTabView on its own — nothing to do here.
    private func advanceAfterAuthentication() {
        guard appState.isAuthenticated else { return }
        if !appState.isOnboarded {
            viewModel.continueAfterAuthentication()
        }
    }
}

/// Renders an official Kakao/Naver brand-login asset as a full-width button. The asset already
/// contains the logo and localized label, so it fills the same height/corner-radius as the Apple
/// button to keep the three-button stack uniform. `.resizable()` without an aspect ratio pins the
/// height exactly (a negligible horizontal scale on these ~6.6:1 assets) without cropping the logo.
private struct BrandLoginButton: View {
    let asset: String
    let isDisabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(asset)
                .resizable()
                .frame(maxWidth: .infinity)
                .frame(height: LandingView.buttonHeight)
                .clipShape(RoundedRectangle(cornerRadius: LandingView.buttonCornerRadius))
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
