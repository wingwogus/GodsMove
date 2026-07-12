//
//  OnboardingProgressBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct OnboardingProgressBar: View {
    let currentStep: OnboardingViewModel.Step

    private let progressSteps: [OnboardingViewModel.Step] = [
        .basicProfile,
        .farmLocation,
        .cropSelection,
        .complete
    ]

    private var currentIndex: Int {
        progressSteps.firstIndex(of: currentStep) ?? 0
    }

    var body: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(Array(progressSteps.enumerated()), id: \.offset) { index, _ in
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
