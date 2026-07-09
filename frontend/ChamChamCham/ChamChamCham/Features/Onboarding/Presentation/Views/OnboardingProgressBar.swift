//
//  OnboardingProgressBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct OnboardingProgressBar: View {
    let currentStep: OnboardingViewModel.Step

    private var currentIndex: Int {
        OnboardingViewModel.Step.allCases.firstIndex(of: currentStep) ?? 0
    }

    var body: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(Array(OnboardingViewModel.Step.allCases.enumerated()), id: \.offset) { index, _ in
                Capsule()
                    .fill(index <= currentIndex ? Color.appPrimary : Color(.systemGray4))
                    .frame(height: 4)
            }
        }
    }
}

#Preview {
    OnboardingProgressBar(currentStep: .basicProfile)
        .padding()
}
