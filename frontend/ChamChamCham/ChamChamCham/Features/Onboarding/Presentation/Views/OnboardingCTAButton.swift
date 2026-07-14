//
//  OnboardingCTAButton.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import SwiftUI

struct OnboardingCTAButton: View {
    let title: String
    let isVisuallyEnabled: Bool
    var isInteractionEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .appTypography(.bodyLarge)
                .foregroundStyle(isVisuallyEnabled ? Color.Text.inverse : Color.Text.muted)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(isVisuallyEnabled ? Color.Object.bold : Color.Object.disabled)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!isInteractionEnabled)
    }
}
