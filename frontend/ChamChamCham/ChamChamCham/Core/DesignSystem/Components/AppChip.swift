//
//  AppChip.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `chip`. A selectable pill: selected shows a soft green fill with a green border and text;
/// unselected is a neutral grey fill. Pass `systemImage` for the optional trailing icon.
struct AppChip: View {
    let label: String
    var isSelected: Bool = false
    var systemImage: String? = nil
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            HStack(spacing: 2) {
                Text(label)
                    .appTypography(.labelMedium)
                    .foregroundStyle(textColor)
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 16))
                        .foregroundStyle(textColor)
                        .frame(width: 24, height: 24)
                }
            }
            .padding(.vertical, 8)
            .padding(.leading, 12)
            .padding(.trailing, systemImage == nil ? 12 : 10)
            .frame(minWidth: 48, minHeight: 32)
            .background(backgroundColor)
            .overlay {
                if isSelected {
                    Capsule().stroke(Color.Border.primary, lineWidth: 1)
                }
            }
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var backgroundColor: Color {
        isSelected ? Color.Object.primarySubtle : Color.Object.muted
    }

    private var textColor: Color {
        isSelected ? Color.Text.primary : Color.Text.subtle
    }
}

#Preview {
    VStack(spacing: Spacing.md) {
        HStack(spacing: Spacing.sm) {
            AppChip(label: "레이블", isSelected: true)
            AppChip(label: "레이블", isSelected: false)
        }
        HStack(spacing: Spacing.sm) {
            AppChip(label: "레이블", isSelected: true, systemImage: "checkmark")
            AppChip(label: "레이블", isSelected: false, systemImage: "checkmark")
        }
    }
    .padding()
}
