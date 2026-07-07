//
//  AppSegmentedControl.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `segmented-control`. A pill track (`object/muted`) with equal-width segments; the selected
/// segment is a white pill with default-colored text, others are muted.
struct AppSegmentedControl: View {
    let titles: [String]
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
                let isSelected = index == selection
                Button {
                    selection = index
                } label: {
                    Text(title)
                        .appTypography(.titleMedium)
                        .foregroundStyle(isSelected ? Color.Text.default : Color.Text.subtle)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, Spacing.sm)
                        .background {
                            if isSelected {
                                Capsule().fill(Color.Object.default)
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(Spacing.xs)
        .background(Capsule().fill(Color.Object.muted))
    }
}

#Preview {
    struct Demo: View {
        @State private var selection = 0
        var body: some View {
            AppSegmentedControl(titles: ["레이블", "레이블"], selection: $selection)
                .padding()
        }
    }
    return Demo()
}
