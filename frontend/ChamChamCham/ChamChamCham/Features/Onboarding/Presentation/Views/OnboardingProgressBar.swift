//
//  OnboardingProgressBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct OnboardingProgressBar: View {
    let currentStep: OnboardingViewModel.Step

    private var progress: CGFloat {
        Self.progressFraction(for: currentStep)
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.Object.muted)

                Capsule()
                    .fill(Color.Object.primary)
                    .frame(width: proxy.size.width * progress)
            }
        }
        .frame(height: 4)
    }

    static func progressFraction(for step: OnboardingViewModel.Step) -> CGFloat {
        switch step {
        case .landing:
            0
        case .basicProfile:
            0.25
        case .farmLocation:
            0.5
        case .cropSelection:
            0.75
        case .complete:
            1.0
        }
    }
}

#Preview {
    OnboardingProgressBar(currentStep: .basicProfile)
        .padding()
}
