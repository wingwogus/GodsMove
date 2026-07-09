//
//  AppTextField.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

/// Figma `text-input`. States (default / focus / filled / error / disabled) are derived from
/// SwiftUI state rather than a manual variant: focus from `@FocusState`, filled from `text`,
/// error from a non-nil `errorMessage`, and disabled from the `.disabled(_:)` environment.
struct AppTextField: View {
    var label: String?
    let placeholder: String
    @Binding var text: String
    var isRequired: Bool = false
    var helperText: String? = nil
    /// When non-nil, the field renders the error variant (red border) and shows this string,
    /// taking precedence over `helperText`.
    var errorMessage: String? = nil
    var keyboardType: UIKeyboardType = .default
    var autoFocus: Bool = false

    @FocusState private var isFocused: Bool
    @Environment(\.isEnabled) private var isEnabled

    private var isError: Bool { errorMessage != nil }

    var body: some View {
        AppFieldContainer(
            label: label,
            isRequired: isRequired,
            helperText: errorMessage ?? helperText,
            isError: isError,
            isFocused: isFocused
        ) {
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    Text(placeholder)
                        .foregroundStyle(isEnabled ? Color.Text.muted : Color.Text.disabled)
                }
                TextField("", text: $text)
                    .foregroundStyle(isEnabled ? Color.Text.default : Color.Text.disabled)
                    .focused($isFocused)
                    .keyboardType(keyboardType)
            }
            .appTypography(.bodyLarge)
            .frame(maxWidth: .infinity, alignment: .leading)

            if isFocused && !text.isEmpty && isEnabled {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundStyle(Color.Icon.subtle)
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
            }
        }
        .onAppear {
            guard autoFocus else { return }
            isFocused = true
        }
    }
}

#Preview {
    struct Demo: View {
        @State private var empty = ""
        @State private var filled = "텍스트"
        var body: some View {
            VStack(spacing: Spacing.lg) {
                AppTextField(label: "레이블", placeholder: "텍스트", text: $empty, isRequired: true,
                             helperText: "메시지를 전달합니다.")
                AppTextField(label: "레이블", placeholder: "텍스트", text: $filled, isRequired: true,
                             helperText: "메시지를 전달합니다.")
                AppTextField(label: "레이블", placeholder: "텍스트", text: $empty, isRequired: true,
                             errorMessage: "메시지를 전달합니다.")
                AppTextField(label: "레이블", placeholder: "텍스트", text: $empty, isRequired: true,
                             helperText: "메시지를 전달합니다.")
                    .disabled(true)
            }
            .padding()
        }
    }
    return Demo()
}
