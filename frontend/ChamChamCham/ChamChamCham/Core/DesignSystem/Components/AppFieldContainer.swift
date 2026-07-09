//
//  AppFieldContainer.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Shared chrome for form inputs (Figma `text-input` / `date-input`): a `레이블 *` heading,
/// a bordered field box, and a helper/error line. `AppTextField` and `AppDateField` compose it
/// so the box styling and state colors live in one place.
struct AppFieldContainer<Content: View>: View {
    let label: String?
    var isRequired: Bool = false
    /// Helper text shown under the field. Pass the error message here and set `isError` for the
    /// error variant — the caller decides which string wins.
    var helperText: String? = nil
    var isError: Bool = false
    /// Drives the green focus border. Text fields pass `@FocusState`; the date field passes its
    /// picker-presented flag.
    var isFocused: Bool = false
    /// 8 for single-line inputs; the text area passes 12 to match its larger box.
    var cornerRadius: CGFloat = 8

    @Environment(\.isEnabled) private var isEnabled
    @ViewBuilder let content: Content

    // Figma field metrics — no Spacing token matches 14/12, so they stay literal to the spec.
    private let verticalPadding: CGFloat = 14
    private let fieldSpacing: CGFloat = 12

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if let label {
                HStack(spacing: 2) {
                    Text(label)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.default)
                    if isRequired {
                        Text("*")
                            .appTypography(.bodyMedium)
                            .foregroundStyle(Color.Text.red)
                    }
                }
            }

            HStack(spacing: fieldSpacing) {
                content
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, verticalPadding)
            .background(RoundedRectangle(cornerRadius: cornerRadius).fill(backgroundColor))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(borderColor, lineWidth: 1)
            )

            if let helperText {
                Text(helperText)
                    .appTypography(.labelMedium)
                    .foregroundStyle(isError ? Color.Text.red : Color.Text.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var backgroundColor: Color {
        isEnabled ? Color.Object.default : Color.Object.disabledSubtle
    }

    private var borderColor: Color {
        if !isEnabled { return Color.Border.default }
        if isError { return Color.Border.error }
        if isFocused { return Color.Border.primary }
        return Color.Border.default
    }
}
