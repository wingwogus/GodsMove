//
//  AppDropdown.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `select` / dropdown. Shares ``AppTextField``'s label / box / helper chrome but shows a
/// placeholder or the selected value plus a chevron that flips while the option sheet is open.
/// Per the Figma spec the border stays neutral (it does not turn green on focus).
struct AppDropdown<Option: Hashable>: View {
    var label: String?
    var placeholder: String = "메뉴를 선택해주세요."
    let options: [Option]
    @Binding var selection: Option?
    let optionTitle: (Option) -> String
    var isRequired: Bool = false
    var helperText: String? = nil
    var errorMessage: String? = nil

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
        optionTitle: @escaping (Option) -> String
    ) {
        self.label = label
        self.placeholder = placeholder
        self.options = options
        self._selection = selection
        self.isRequired = isRequired
        self.helperText = helperText
        self.errorMessage = errorMessage
        self.optionTitle = optionTitle
    }

    private var isError: Bool { errorMessage != nil }

    var body: some View {
        AppFieldContainer(
            label: label,
            isRequired: isRequired,
            helperText: errorMessage ?? helperText,
            isError: isError,
            isFocused: false
        ) {
            Text(displayText)
                .appTypography(.bodyLarge)
                .foregroundStyle(valueColor)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
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
        return selection == nil ? Color.Text.muted : Color.Text.default
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
        errorMessage: String? = nil
    ) {
        self.init(
            label,
            placeholder: placeholder,
            options: options,
            selection: selection,
            isRequired: isRequired,
            helperText: helperText,
            errorMessage: errorMessage,
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
            }
            .padding()
        }
    }
    return Demo()
}
