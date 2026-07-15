//
//  AppTextEditor.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `text-area`. A multi-line variant of ``AppTextField`` — same label / border / helper
/// chrome (via ``AppFieldContainer``) but a 200pt box (radius 12) with a bottom-right character
/// counter. Pass `characterLimit` to show `count/limit` and cap input.
struct AppTextEditor: View {
    var label: String?
    let placeholder: String
    @Binding var text: String
    var isRequired: Bool = false
    var helperText: String? = nil
    var errorMessage: String? = nil
    var characterLimit: Int? = nil
    /// Mirrors Figma's separate `filled` property while retaining text-derived state by default.
    var filled: Bool? = nil

    @FocusState private var isFocused: Bool
    @Environment(\.isEnabled) private var isEnabled

    private var isError: Bool { errorMessage != nil }
    private var isFilled: Bool {
        AppFieldContainer<EmptyView>.resolvedFilled(override: filled, hasValue: !text.isEmpty)
    }

    var body: some View {
        AppFieldContainer(
            label: label,
            isRequired: isRequired,
            helperText: errorMessage ?? helperText,
            isError: isError,
            isFocused: isFocused,
            cornerRadius: 12
        ) {
            VStack(spacing: Spacing.sm) {
                ZStack(alignment: .topLeading) {
                    if text.isEmpty {
                        Text(placeholder)
                            .foregroundStyle(isEnabled ? Color.Text.muted : Color.Text.disabled)
                            .padding(.top, 8)
                            .padding(.leading, 5)
                    }
                    TextEditor(text: $text)
                        .foregroundStyle(isEnabled ? (isFilled ? Color.Text.default : Color.Text.muted) : Color.Text.disabled)
                        .scrollContentBackground(.hidden)
                        .focused($isFocused)
                        .onChange(of: text) { _, newValue in
                            guard let limit = characterLimit, newValue.count > limit else { return }
                            text = String(newValue.prefix(limit))
                        }
                }
                .appTypography(.bodyLarge)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

                Text(counterText)
                    .appTypography(.labelMedium)
                    .foregroundStyle(isEnabled ? (isFilled ? Color.Text.default : Color.Text.muted) : Color.Text.disabled)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .frame(height: 172)
        }
    }

    private var counterText: String {
        if let characterLimit {
            return "\(text.count)/\(characterLimit)"
        }
        return "\(text.count)"
    }
}

#Preview {
    struct Demo: View {
        @State private var empty = ""
        @State private var filled = "텍스트"
        var body: some View {
            ScrollView {
                VStack(spacing: Spacing.lg) {
                    AppTextEditor(label: "레이블", placeholder: "텍스트", text: $empty,
                                  isRequired: true, helperText: "메시지를 전달합니다.", characterLimit: 200)
                    AppTextEditor(label: "레이블", placeholder: "텍스트", text: $filled,
                                  isRequired: true, errorMessage: "메시지를 전달합니다.", characterLimit: 200)
                    AppTextEditor(label: "레이블", placeholder: "텍스트", text: $empty,
                                  isRequired: true, helperText: "메시지를 전달합니다.")
                        .disabled(true)
                }
                .padding()
            }
        }
    }
    return Demo()
}
