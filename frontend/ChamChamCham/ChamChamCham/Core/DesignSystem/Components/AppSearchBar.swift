//
//  AppSearchBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `search`. A borderless search field on a subtle fill with a leading magnifier and a
/// trailing clear button that appears only while focused with text (Figma's `focus` variant) —
/// `filled` (has text, not focused) shows no clear button. `isError` renders the `error` variant's
/// red border; Figma doesn't pair an error state with helper text here, so there's no message slot.
struct AppSearchBar: View {
    @Binding var text: String
    var placeholder: String = "검색어를 입력해주세요."
    var isError: Bool = false
    /// Lets a parent own focus (e.g. to drop it proactively before a tap-driven navigation so the
    /// tap isn't swallowed dismissing the keyboard). `nil` (default) keeps the previous
    /// internally-owned-focus behavior for existing callers.
    var externalFocus: FocusState<Bool>.Binding? = nil

    @FocusState private var internalFocus: Bool
    @Environment(\.isEnabled) private var isEnabled

    private var isFocused: Bool {
        externalFocus?.wrappedValue ?? internalFocus
    }

    var body: some View {
        HStack(spacing: 12) {
            // Figma: search icon ↔ text group uses gap 8; the trailing clear button sits at gap 12.
            HStack(spacing: Spacing.sm) {
                AppIconView(source: .asset("search"), size: 24)
                    .foregroundStyle(Color.Icon.subtle)

                ZStack(alignment: .leading) {
                    if text.isEmpty {
                        Text(placeholder)
                            .foregroundStyle(Color.Text.muted)
                    }
                    TextField("", text: $text)
                        .foregroundStyle(Color.Text.default)
                        .focused(externalFocus ?? $internalFocus)
                }
                .appTypography(.bodyLarge)
            }
            .frame(maxWidth: .infinity)

            if !text.isEmpty && isFocused && isEnabled {
                Button {
                    text = ""
                } label: {
                    AppIconView(source: .asset("cancel"), size: 24)
                        .foregroundStyle(Color.Icon.subtle)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color.Object.subtle))
        .overlay {
            if isError {
                RoundedRectangle(cornerRadius: 8).stroke(Color.Border.error, lineWidth: 1)
            }
        }
    }
}

#Preview {
    struct Demo: View {
        @State private var empty = ""
        @State private var filled = "감자"
        var body: some View {
            VStack(spacing: Spacing.md) {
                AppSearchBar(text: $empty)
                // Tap into this one in Live Preview to focus it — the clear button only shows up
                // while focused with text, matching Figma's `focus` variant (not `filled`).
                AppSearchBar(text: $filled)
                AppSearchBar(text: $empty, isError: true)
            }
            .padding()
        }
    }
    return Demo()
}
