//
//  AuthFlowView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct AuthFlowView: View {
    @State private var viewModel = OnboardingViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            content
        }
        .environment(viewModel)
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                viewModel.persist()
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
        case .cropSelection, .farmLocation, .complete:
            Text("다음 단계는 아직 준비 중입니다")
                .font(.appBody)
                .foregroundStyle(Color.appTextSecondary)
        }
    }
}
