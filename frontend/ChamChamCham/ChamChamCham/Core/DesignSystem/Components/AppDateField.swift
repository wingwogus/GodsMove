//
//  AppDateField.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `date-input`. Renders like a text field with a trailing calendar icon and a
/// `yyyy-mm-dd` placeholder; tapping presents a graphical date picker. `selection` is optional so
/// the empty state shows the placeholder. Focus (green border) is driven by the picker being open.
struct AppDateField: View {
    var label: String?
    var placeholder: String = "yyyy-mm-dd"
    @Binding var selection: Date?
    var isRequired: Bool = false
    var helperText: String? = nil
    /// When non-nil, the field renders the error variant (red border) and shows this string,
    /// taking precedence over `helperText`.
    var errorMessage: String? = nil
    /// Mirrors Figma's separate `filled` property while retaining selection-derived state by default.
    var filled: Bool? = nil

    @Environment(\.isEnabled) private var isEnabled
    @State private var isPickerPresented = false

    private var isError: Bool { errorMessage != nil }
    private var isFilled: Bool {
        AppFieldContainer<EmptyView>.resolvedFilled(override: filled, hasValue: selection != nil)
    }

    var body: some View {
        AppFieldContainer(
            label: label,
            isRequired: isRequired,
            helperText: errorMessage ?? helperText,
            isError: isError,
            isFocused: isPickerPresented
        ) {
            Text(displayText)
                .appTypography(.bodyLarge)
                .foregroundStyle(valueColor)
                .frame(maxWidth: .infinity, alignment: .leading)

            AppIconView(source: .asset("calendar_month"), size: 24)
                .foregroundStyle(iconColor)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            guard isEnabled else { return }
            isPickerPresented = true
        }
        .sheet(isPresented: $isPickerPresented) {
            pickerSheet
        }
    }

    private var displayText: String {
        guard let selection else { return placeholder }
        return Self.formatter.string(from: selection)
    }

    private var valueColor: Color {
        if !isEnabled { return Color.Text.disabled }
        return isFilled ? Color.Text.default : Color.Text.muted
    }

    private var iconColor: Color {
        Self.iconColor(isEnabled: isEnabled, isError: isError)
    }

    static func iconColor(isEnabled: Bool, isError: Bool) -> Color {
        if !isEnabled { return Color.Icon.disabled }
        if isError { return Color.Icon.subtle }
        return Color.Icon.default
    }

    private var pickerSheet: some View {
        NavigationStack {
            DatePicker(
                "",
                selection: Binding(
                    get: { selection ?? Date() },
                    set: { selection = $0 }
                ),
                in: ...Date(),
                displayedComponents: .date
            )
            .datePickerStyle(.graphical)
            .labelsHidden()
            .padding()
            .navigationTitle(label ?? "날짜 선택")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("완료") { isPickerPresented = false }
                }
            }
        }
        .presentationDetents([.medium])
    }

    static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

#Preview {
    struct Demo: View {
        @State private var empty: Date?
        @State private var picked: Date? = Date()
        var body: some View {
            VStack(spacing: Spacing.lg) {
                AppDateField(label: "레이블", selection: $empty, isRequired: true,
                             helperText: "메시지를 전달합니다.")
                AppDateField(label: "레이블", selection: $picked, isRequired: true,
                             helperText: "메시지를 전달합니다.")
                AppDateField(label: "레이블", selection: $empty, isRequired: true,
                             errorMessage: "메시지를 전달합니다.")
                AppDateField(label: "레이블", selection: $empty, isRequired: true,
                             helperText: "메시지를 전달합니다.")
                    .disabled(true)
            }
            .padding()
        }
    }
    return Demo()
}
