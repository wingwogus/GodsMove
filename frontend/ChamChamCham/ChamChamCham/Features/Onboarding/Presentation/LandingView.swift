//
//  LandingView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct LandingView: View {
    @Environment(OnboardingViewModel.self) private var viewModel

    var body: some View {
        VStack(spacing: Spacing.md) {
            PrimaryButton(title: "카카오로 시작하기") { viewModel.goNext() }
            PrimaryButton(title: "네이버로 시작하기") { viewModel.goNext() }
            PrimaryButton(title: "Apple로 시작하기") { viewModel.goNext() }
        }
        .padding(Spacing.lg)
    }
}

#Preview {
    LandingView()
        .environment(OnboardingViewModel())
}
