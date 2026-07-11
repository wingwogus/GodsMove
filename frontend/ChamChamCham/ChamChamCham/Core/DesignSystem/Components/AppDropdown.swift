//
//  AppDropdown.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `drop-down`. Shares ``AppTextField``'s label / box / helper chrome but maps runtime state
/// to the Figma props: `variant` (`default` / `filled` / `error`) and `focus` (`true` / `false`).
/// Per the Figma spec the focus prop flips the chevron but keeps the field border neutral.
struct AppDropdown<Option: Hashable>: View {
    enum Variant: Equatable {
        case `default`
        case filled
        case error
    }

    var label: String?
    var placeholder: String = "메뉴를 선택해주세요."
    let options: [Option]
    @Binding var selection: Option?
    let optionTitle: (Option) -> String
    var isRequired: Bool = false
    var helperText: String? = nil
    var errorMessage: String? = nil
    /// Optional Figma `filled` override. When omitted, the selection remains the source of truth.
    var filled: Bool? = nil

    @Environment(\.isEnabled) private var isEnabled
    @State private var isExpanded = false

    init(
        _ label: String? = nil,
        placeholder: String = "메뉴를 선택해주세요.",
        options: [Option],
        selection: Binding<Option?>,
        isRequired: Bool = false,
        helperText: String? = nil,
        errorMessage: String? = nil,
        filled: Bool? = nil,
        optionTitle: @escaping (Option) -> String
    ) {
        self.label = label
        self.placeholder = placeholder
        self.options = options
        self._selection = selection
        self.isRequired = isRequired
        self.helperText = helperText
        self.errorMessage = errorMessage
        self.filled = filled
        self.optionTitle = optionTitle
    }

    private var isError: Bool { errorMessage != nil }
    private var isFilled: Bool {
        AppFieldContainer<EmptyView>.resolvedFilled(override: filled, hasValue: selection != nil)
    }
    private var currentVariant: Variant { Self.variant(isFilled: isFilled, isError: isError) }

    var body: some View {
        AppFieldContainer(
            label: label,
            isRequired: isRequired,
            helperText: errorMessage ?? helperText,
            isError: isError,
            // The Figma focus prop only flips the dropdown icon; it does not turn the border green.
            isFocused: false
        ) {
            Text(displayText)
                .appTypography(.bodyLarge)
                .foregroundStyle(valueColor)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: Self.chevronSystemName(isFocused: isExpanded))
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.Icon.subtle)
                .frame(width: 24, height: 24)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            guard isEnabled else { return }
            isExpanded = true
        }
        .sheet(isPresented: $isExpanded) {
            optionsSheet
        }
    }

    private var displayText: String {
        guard let selection else { return placeholder }
        return optionTitle(selection)
    }

    private var valueColor: Color {
        if !isEnabled { return Color.Text.disabled }
        switch currentVariant {
        case .default:
            return Color.Text.muted
        case .filled:
            return Color.Text.default
        case .error:
            return isFilled ? Color.Text.default : Color.Text.muted
        }
    }

    static func variant(isFilled: Bool, isError: Bool) -> Variant {
        if isError { return .error }
        return isFilled ? .filled : .default
    }

    static func chevronSystemName(isFocused: Bool) -> String {
        isFocused ? "chevron.up" : "chevron.down"
    }

    private var optionsSheet: some View {
        NavigationStack {
            List(options, id: \.self) { option in
                Button {
                    selection = option
                    isExpanded = false
                } label: {
                    HStack {
                        Text(optionTitle(option))
                            .appTypography(.bodyLarge)
                            .foregroundStyle(Color.Text.default)
                        Spacer()
                        if selection == option {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.Icon.primary)
                        }
                    }
                }
            }
            .navigationTitle(label ?? "선택")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}

extension AppDropdown where Option == String {
    init(
        _ label: String? = nil,
        placeholder: String = "메뉴를 선택해주세요.",
        options: [String],
        selection: Binding<String?>,
        isRequired: Bool = false,
        helperText: String? = nil,
        errorMessage: String? = nil,
        filled: Bool? = nil
    ) {
        self.init(
            label,
            placeholder: placeholder,
            options: options,
            selection: selection,
            isRequired: isRequired,
            helperText: helperText,
            errorMessage: errorMessage,
            filled: filled,
            optionTitle: { $0 }
        )
    }
}

#Preview {
    struct Demo: View {
        @State private var selected: String?
        var body: some View {
            VStack(spacing: Spacing.lg) {
                AppDropdown("레이블", options: ["감자", "고구마", "당근"], selection: $selected,
                            isRequired: true, helperText: "메시지를 전달합니다.")
                AppDropdown("레이블", options: ["감자", "고구마"], selection: .constant("감자"),
                            isRequired: true, helperText: "메시지를 전달합니다.")
                AppDropdown("레이블", options: ["감자", "고구마"], selection: .constant(nil),
                            isRequired: true, errorMessage: "메시지를 전달합니다.")
                AppDropdown("레이블", options: ["감자", "고구마"], selection: .constant("감자"),
                            isRequired: true, errorMessage: "메시지를 전달합니다.")
            }
            .padding()
        }
    }
    return Demo()
}
