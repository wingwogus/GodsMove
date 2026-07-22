//
//  CropSelectionBody.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Shared crop-selection layout: a title header, a sticky search field + category tabs, a selectable
/// crop list, and a bottom tray of selected-crop chips with a CTA. Used by the onboarding crop step
/// (`CropSelectionView`) and the reusable `CropPickerView`.
///
/// State-agnostic: the caller owns the crop catalog, the (ordered) selection, the selection limit, and
/// any persistence. The body only manages its own search text and category-tab selection.
struct CropSelectionBody: View {
    enum ToggleOutcome {
        case changed
        case limitReached
    }

    let title: String
    let subtitle: String

    let crops: [Crop]
    let categories: [CropCategory]
    let isLoading: Bool
    let loadError: String?
    let onRetry: () -> Void

    /// Selected crop ids in selection order — drives the bottom tray chips.
    let selectedCropIDs: [UUID]
    /// Maximum selectable crops, or `nil` for unlimited. Only used for the limit message text; the
    /// caller's `onToggle` is the authority on whether a selection is rejected.
    let selectionLimit: Int?
    let onToggle: (UUID) -> ToggleOutcome

    let ctaTitle: String
    let onComplete: () -> Void

    @State private var searchText = ""
    @State private var selectedCategoryCode: String?
    @State private var selectionLimitMessage: String?

    /// `crops` sorted once whenever it changes, so tab switches and searches only filter — they never re-sort.
    @State private var sortedCrops: [Crop] = []
    /// O(1) id → crop lookup for the selected-chip labels, instead of a linear scan per chip.
    @State private var cropsByID: [UUID: Crop] = [:]

    private var selectedCount: Int { selectedCropIDs.count }

    /// `nil` means the "전체" tab: no category filter applied.
    private var filteredCrops: [Crop] {
        let byCategory = selectedCategoryCode.map { code in
            sortedCrops.filter { $0.categoryCode == code }
        } ?? sortedCrops

        let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return keyword.isEmpty
            ? byCategory
            : byCategory.filter { $0.name.localizedCaseInsensitiveContains(keyword) }
    }

    /// Tab 0 is always "전체"; tabs 1... mirror `categories`.
    private var categoryTabTitles: [String] {
        ["전체"] + categories.map(\.label)
    }

    private var selectedCategoryIndex: Int {
        guard let selectedCategoryCode,
              let index = categories.firstIndex(where: { $0.code == selectedCategoryCode }) else {
            return 0
        }
        return index + 1
    }

    private var selectedCategoryBinding: Binding<Int> {
        Binding {
            selectedCategoryIndex
        } set: { index in
            if index == 0 {
                selectedCategoryCode = nil
            } else if categories.indices.contains(index - 1) {
                selectedCategoryCode = categories[index - 1].code
            }
            selectionLimitMessage = nil
        }
    }

    private var selectedCropChips: [SelectedCropChip] {
        selectedCropIDs.map { cropID in
            let cropName = cropsByID[cropID]?.name ?? "선택 작물"
            return SelectedCropChip(id: cropID, name: cropName)
        }
    }

    private var selectedCropRows: [[SelectedCropChip]] {
        stride(from: 0, to: selectedCropChips.count, by: 3).map { startIndex in
            Array(selectedCropChips[startIndex..<min(startIndex + 3, selectedCropChips.count)])
        }
    }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    titleHeader
                        .padding(.horizontal, 20)
                        .padding(.bottom, 8)

                    Section {
                        cropList
                            .padding(.bottom, selectedCount == 0 ? 132 : 224)
                    } header: {
                        stickyControls
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.Background.default)
            .ignoresSafeArea(.container, edges: .bottom)
            .dismissKeyboardOnTap()
            .overlay(alignment: .bottom) {
                bottomTray
                    .padding(.bottom, -proxy.safeAreaInsets.bottom)
            }
            // 키보드가 하단 트레이(선택 칩 + CTA)를 밀어 올리지 않도록 고정한다 (SearchView와 동일 패턴).
            .ignoresSafeArea(.keyboard, edges: .bottom)
        }
        .task(id: crops) {
            rebuildCropCaches()
        }
    }

    private func rebuildCropCaches() {
        sortedCrops = crops.sorted { lhs, rhs in
            lhs.name.localizedStandardCompare(rhs.name) == .orderedAscending
        }
        cropsByID = Dictionary(uniqueKeysWithValues: crops.map { ($0.id, $0) })
    }

    @ViewBuilder
    private var cropList: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 64)
        } else if let loadError {
            VStack(spacing: Spacing.sm) {
                Text(loadError)
                    .font(AppTypography.bodyMedium.font)
                    .foregroundStyle(Color.Text.subtle)
                Button("다시 시도") {
                    onRetry()
                }
                .font(AppTypography.bodyMediumEmphasized.font)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 64)
        } else if filteredCrops.isEmpty {
            VStack(spacing: Spacing.sm) {
                AppIconView(source: .asset("search"), size: 28)
                    .foregroundStyle(Color.Icon.subtle)
                Text("검색 결과가 없어요")
                    .font(AppTypography.bodyMediumEmphasized.font)
                    .foregroundStyle(Color.Text.default)
                Text("작물명이나 카테고리를 다시 확인해주세요.")
                    .font(AppTypography.labelMedium.font)
                    .foregroundStyle(Color.Text.subtle)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 64)
        } else {
            VStack(spacing: 0) {
                ForEach(Array(filteredCrops.enumerated()), id: \.element.id) { index, crop in
                    cropRow(crop)
                    if index < filteredCrops.count - 1 {
                        Divider()
                            .background(Color.Border.default)
                            .padding(.leading, 20)
                    }
                }
            }
        }
    }

    private var titleHeader: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .appTypography(.headlineMediumEmphasized)
                .foregroundStyle(Color.Text.default)

            Text(subtitle)
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var stickyControls: some View {
        VStack(spacing: 0) {
            searchField
                .padding(.horizontal, 10)
                .padding(.vertical, Spacing.md)

            categoryTabs

            Divider()
                .background(Color.Border.subtle)
        }
        .background(Color.Background.default)
    }

    private var searchField: some View {
        AppSearchBar(text: $searchText, placeholder: "작물명을 입력해주세요.")
            .frame(height: 56)
    }

    private var categoryTabs: some View {
        AppTabBar(
            titles: categoryTabTitles,
            selection: selectedCategoryBinding,
            scrollable: true
        )
        .frame(height: 56)
    }

    private func cropRow(_ crop: Crop) -> some View {
        let isSelected = selectedCropIDs.contains(crop.id)
        return Button {
            toggle(crop)
        } label: {
            HStack(spacing: Spacing.md) {
                Text(crop.name)
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(isSelected ? Color.Text.primary : Color.Text.subtle)
                    .lineLimit(1)

                Spacer()

                if isSelected {
                    AppIconView(source: .asset("check_circle"), size: 24)
                        .foregroundStyle(Color.Object.primary)
                }
            }
            .padding(.horizontal, 20)
            .frame(height: 58)
            .frame(maxWidth: .infinity)
            .background(isSelected ? Color.Object.primarySubtle : Color.Background.default)
        }
        .buttonStyle(.plain)
    }

    private var bottomTray: some View {
        VStack(spacing: Spacing.md) {
            if !selectedCropChips.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(selectedCropRows.enumerated()), id: \.offset) { _, row in
                        HStack(spacing: 8) {
                            ForEach(row) { crop in
                                selectedCropChip(crop)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if let selectionLimitMessage {
                Text(selectionLimitMessage)
                    .font(AppTypography.labelMedium.font)
                    .foregroundStyle(Color.Text.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            OnboardingCTAButton(title: ctaTitle, isVisuallyEnabled: selectedCount > 0) {
                guard selectedCount > 0 else { return }
                onComplete()
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, selectedCropChips.isEmpty ? 12 : 16)
        .padding(.bottom, 32)
        .background {
            if selectedCropChips.isEmpty {
                Color.Background.default
            } else {
                UnevenRoundedRectangle(cornerRadii: .init(topLeading: 32, topTrailing: 32))
                    .fill(Color.Background.default)
                    .shadow(color: .black.opacity(0.08), radius: 16, y: -4)
            }
        }
        .overlay(alignment: .top) {
            if selectedCropChips.isEmpty {
                Rectangle()
                    .fill(Color.Border.subtle)
                    .frame(height: 1)
            }
        }
    }

    private func selectedCropChip(_ crop: SelectedCropChip) -> some View {
        AppChip(label: crop.name, isSelected: true, style: .solidPastel, trailingSystemImage: .asset("close_small")) {
            _ = onToggle(crop.id)
            selectionLimitMessage = nil
        }
    }

    private func toggle(_ crop: Crop) {
        switch onToggle(crop.id) {
        case .changed:
            selectionLimitMessage = nil
        case .limitReached:
            if let selectionLimit {
                selectionLimitMessage = "작물은 최대 \(selectionLimit)개까지 선택할 수 있어요."
            }
        }
    }

    private struct SelectedCropChip: Identifiable {
        let id: UUID
        let name: String
    }
}
