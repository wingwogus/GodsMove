//
//  AuthFlowView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct AuthFlowView: View {
    let container: DIContainer
    @State private var viewModel: OnboardingViewModel
    @State private var authViewModel: AuthViewModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(AppState.self) private var appState

    init(container: DIContainer) {
        self.container = container
        _viewModel = State(
            initialValue: OnboardingViewModel(
                onboardingRepository: container.makeOnboardingRepository(),
                mediaUploadRepository: container.makeMediaUploadRepository(),
                cropCatalogService: container.makeCropCatalogService(),
                memberProfileCache: container.memberProfileCache
            )
        )
        _authViewModel = State(
            initialValue: AuthViewModel(
                authRepository: container.makeAuthRepository(),
                memberProfileCache: container.memberProfileCache
            )
        )
    }

    var body: some View {
        NavigationStack {
            content
        }
        .environment(viewModel)
        .environment(authViewModel)
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                viewModel.persist()
            }
        }
        .task {
            // A refresh-token-authenticated user should never land back on the login buttons — this only
            // happens if no draft snapshot was ever saved (e.g. Keychain survived an app delete+reinstall).
            if appState.isAuthenticated, viewModel.currentStep == .landing {
                viewModel.jump(to: .basicProfile)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.currentStep {
        case .landing:
            LandingView()
        case .basicProfile:
            BasicProfileView()
        case .cropSelection:
            CropSelectionView()
        case .farmLocation:
            FarmLocationView()
        case .complete:
            OnboardingCompleteView()
        }
    }
}
