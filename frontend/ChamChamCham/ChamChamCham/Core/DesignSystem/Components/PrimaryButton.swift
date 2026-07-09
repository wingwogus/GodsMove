//
//  PrimaryButton.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

/// Full-width primary CTA. Thin wrapper over ``AppButton`` so existing onboarding/landing call
/// sites keep their `PrimaryButton(title:action:)` API while getting the design-system button.
struct PrimaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        AppButton(title, variant: .primary, size: .medium, fullWidth: true, action: action)
    }
}
