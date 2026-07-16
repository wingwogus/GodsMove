//
//  AppSelectItem.swift
//  ChamChamCham
//
//  Created by iyungui on 7/15/26.
//

import SwiftUI

/// Figma `select-item`. A single rounded, selectable choice box — callers arrange these in a row
/// (`HStack`/`ForEach`), matching the granularity of `AppChip`/`AppBadge`. Used for flat single-choice
/// pickers where a dropdown would be overkill (record's 모종 번식법, onboarding's 자격).
struct AppSelectItem: View {
    let title: String
    var isSelected: Bool = false
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            Text(title)
                .appTypography(.bodyMedium)
                .foregroundStyle(isSelected ? Color.Text.primary : Color.Text.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(isSelected ? Color.Object.primarySubtle : Color.Object.subtle)
                .overlay {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(isSelected ? Color.Border.primary : Color.Border.subtle, lineWidth: 1)
                }
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    struct Demo: View {
        @State private var selection = 0
        var body: some View {
            HStack(spacing: 8) {
                ForEach(0..<3) { index in
                    AppSelectItem(title: "레이블", isSelected: selection == index) {
                        selection = index
                    }
                }
            }
            .padding()
        }
    }
    return Demo()
}
