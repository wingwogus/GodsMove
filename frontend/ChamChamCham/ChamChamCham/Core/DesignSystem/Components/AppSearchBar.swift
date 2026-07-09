//
//  AppSearchBar.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `search`. A borderless search field on a subtle fill with a leading magnifier and a
/// trailing clear button that appears once there's text.
struct AppSearchBar: View {
    @Binding var text: String
    var placeholder: String = "검색어를 입력해주세요."

    @FocusState private var isFocused: Bool
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 20))
                .foregroundStyle(Color.Icon.subtle)
                .frame(width: 24, height: 24)

            ZStack(alignment: .leading) {
                if text.isEmpty {
                    Text(placeholder)
                        .foregroundStyle(Color.Text.muted)
                }
                TextField("", text: $text)
                    .foregroundStyle(Color.Text.default)
                    .focused($isFocused)
            }
            .appTypography(.bodyLarge)

            if !text.isEmpty && isEnabled {
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
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color.Object.subtle))
    }
}

#Preview {
    struct Demo: View {
        @State private var empty = ""
        @State private var filled = "감자"
        var body: some View {
            VStack(spacing: Spacing.md) {
                AppSearchBar(text: $empty)
                AppSearchBar(text: $filled)
            }
            .padding()
        }
    }
    return Demo()
}
