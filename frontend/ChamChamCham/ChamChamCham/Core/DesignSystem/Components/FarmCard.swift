//
//  FarmCard.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// Figma `setting-card` (밭 카드). A rounded card grouping a farm's name, road address, and
/// crop badges as three tappable rows. `isSelected` reverts the card/row fills (used for the
/// delete-selection state). Rows compose existing tokens and `AppBadge` — no raw values.
struct FarmCard: View {
    var farmName: String = "농지명"
    var roadAddress: String = "재배지 도로명 주소"
    var crops: [String] = []
    var isSelected: Bool = false

    /// Inline 농지명 edit state (owned by the caller, e.g. `FarmListView`'s `editingFarmId`). When
    /// `true`, the name row swaps `rowLabel` for a `TextField` bound to `editingName`; the trailing
    /// icon clears the text, and the keyboard's 완료/done key commits via `onCommitName`. An empty
    /// name shows an error border/message and blocks commit. Defaults keep existing call sites unchanged.
    var isEditingName: Bool = false
    var editingName: Binding<String>? = nil
    var onCommitName: (() -> Void)? = nil

    var onEditName: (() -> Void)? = nil
    var onTapAddress: (() -> Void)? = nil
    var onTapCrops: (() -> Void)? = nil

    // Figma spec: card 350×232, 20 padding, 20 radius; rows 56 high with 12 gap. Only 20/8 map to
    // Spacing tokens; 56/12 stay literal to the component spec (as AppFieldContainer does for 14/12).
    private let rowHeight: CGFloat = 56
    private let rowSpacing: CGFloat = 12
    private let cropDisplayLimit = 3

    @FocusState private var isNameFieldFocused: Bool

    var body: some View {
        VStack(spacing: rowSpacing) {
            nameRow
            row(icon: .asset("chevron_forward"), action: onTapAddress) {
                rowLabel(roadAddress)
            }
            row(icon: .asset("chevron_forward"), action: onTapCrops) {
                cropContent
            }
        }
        .padding(Spacing.lg - Spacing.xs) // 20
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(isSelected ? Color.Object.primarySubtle : Color.Object.default)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(isSelected ? Color.Border.primary : Color.Border.default, lineWidth: 1)
        )
    }

    // MARK: - Name row (inline edit)

    @ViewBuilder private var nameRow: some View {
        if isEditingName, let editingName {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                HStack(spacing: Spacing.sm) {
                    TextField("농지명을 입력해주세요.", text: editingName)
                        .appTypography(.bodyLargeEmphasized)
                        .foregroundStyle(Color.Text.default)
                        .submitLabel(.done)
                        .focused($isNameFieldFocused)
                        .onAppear { isNameFieldFocused = true }
                        .onSubmit { if !isNameEmpty { onCommitName?() } }
                    Spacer(minLength: Spacing.sm)
                    if !editingName.wrappedValue.isEmpty {
                        Button {
                            editingName.wrappedValue = ""
                        } label: {
                            AppIconView(source: .asset("cancel"), size: 24)
                                .foregroundStyle(Color.Icon.subtle)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Spacing.md)
                .frame(maxWidth: .infinity, minHeight: rowHeight, maxHeight: rowHeight, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(isSelected ? Color.Object.default : Color.Object.subtle)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(nameRowBorderColor, lineWidth: 1)
                )
                if isNameEmpty {
                    Text("농지명은 필수로 작성해주세요.")
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                }
            }
        } else {
            row(icon: "pencil", action: onEditName) {
                rowLabel(farmName)
            }
        }
    }

    private var isNameEmpty: Bool {
        editingName?.wrappedValue.trimmingCharacters(in: .whitespaces).isEmpty ?? false
    }

    private var nameRowBorderColor: Color {
        if isNameEmpty { return Color.Border.error }
        if isNameFieldFocused { return Color.Border.primary }
        return Color.Border.default
    }

    // MARK: - Rows

    private func row<Content: View>(
        icon: AppIconSource,
        action: (() -> Void)?,
        @ViewBuilder content: () -> Content
    ) -> some View {
        Button {
            action?()
        } label: {
            HStack(spacing: Spacing.sm) {
                content()
                Spacer(minLength: Spacing.sm)
                AppIconView(source: icon, size: 20)
                    .foregroundStyle(Color.Icon.subtle)
                    .frame(width: 24, height: 24)
            }
            .padding(.horizontal, Spacing.md)
            .frame(maxWidth: .infinity, minHeight: rowHeight, maxHeight: rowHeight, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? Color.Object.default : Color.Object.subtle)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(action == nil)
    }

    private func rowLabel(_ text: String) -> some View {
        Text(text)
            .appTypography(.bodyLargeEmphasized)
            .foregroundStyle(Color.Text.default)
            .lineLimit(1)
    }

    // MARK: - Crops

    @ViewBuilder private var cropContent: some View {
        if crops.isEmpty {
            rowLabel("작물")
        } else {
            HStack(spacing: Spacing.sm) {
                ForEach(Array(crops.prefix(cropDisplayLimit).enumerated()), id: \.offset) { _, crop in
                    AppBadge(label: crop, style: .solidPastel, variant: .primary)
                }
                if crops.count > cropDisplayLimit {
                    Text("외 \(crops.count - cropDisplayLimit)종")
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.subtle)
                        .lineLimit(1)
                }
            }
        }
    }
}

#if DEBUG
#Preview {
    ScrollView {
        VStack(spacing: Spacing.md) {
            FarmCard(
                farmName: "행복농장",
                roadAddress: "경기도 이천시 …",
                crops: ["인삼", "고추", "배추", "무", "파"]
            )
            FarmCard(
                farmName: "행복농장",
                roadAddress: "경기도 이천시 …",
                crops: ["인삼", "고추"],
                isSelected: true
            )
            FarmCard()
        }
        .padding(20)
        .background(Color.Background.subtle)
    }
}
#endif
