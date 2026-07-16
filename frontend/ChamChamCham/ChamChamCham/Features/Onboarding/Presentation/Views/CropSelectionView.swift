//
//  CropSelectionView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct CropSelectionView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @State private var searchText = ""
    @State private var selectedCategoryCode: String?
    @State private var selectionLimitMessage: String?

    private var filteredCrops: [Crop] {
        let selectedCode = selectedCategory?.code
        let byCategory = selectedCode.map { code in
            viewModel.availableCrops.filter { $0.categoryCode == code }
        } ?? viewModel.availableCrops

        let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let filtered = keyword.isEmpty
            ? byCategory
            : byCategory.filter { $0.name.localizedCaseInsensitiveContains(keyword) }

        return filtered.sorted { lhs, rhs in
            lhs.name.localizedStandardCompare(rhs.name) == .orderedAscending
        }
    }

    private var selectedCount: Int { viewModel.draft.cropIDs.count }

    private var selectedCategory: CropCategory? {
        guard !viewModel.cropCategories.isEmpty else { return nil }
        if let selectedCategoryCode,
           let category = viewModel.cropCategories.first(where: { $0.code == selectedCategoryCode }) {
            return category
        }
        return viewModel.cropCategories.first
    }

    private var selectedCategoryIndex: Int {
        guard let selectedCategory else { return 0 }
        return viewModel.cropCategories.firstIndex { $0.code == selectedCategory.code } ?? 0
    }

    private var selectedCategoryBinding: Binding<Int> {
        Binding {
            selectedCategoryIndex
        } set: { index in
            guard viewModel.cropCategories.indices.contains(index) else { return }
            selectedCategoryCode = viewModel.cropCategories[index].code
            selectionLimitMessage = nil
        }
    }

    private var selectedCropChips: [SelectedCropChip] {
        viewModel.draft.cropIDs.map { cropID in
            let cropName = viewModel.availableCrops.first(where: { $0.id == cropID })?.name ?? "선택 작물"
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
            VStack(spacing: 0) {
                topAppBar

                OnboardingProgressBar(currentStep: viewModel.currentStep)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 32)

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
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.Background.default)
            .ignoresSafeArea(.container, edges: .bottom)
            .overlay(alignment: .bottom) {
                bottomTray
                    .padding(.bottom, -proxy.safeAreaInsets.bottom)
            }
        }
        .task {
            await viewModel.loadCropsIfNeeded()
            selectedCategoryCode = selectedCategoryCode ?? viewModel.cropCategories.first?.code
        }
        .onChange(of: viewModel.cropCategories) { _, categories in
            guard selectedCategoryCode == nil else { return }
            selectedCategoryCode = categories.first?.code
        }
    }

    @ViewBuilder
    private var cropList: some View {
        if viewModel.isLoadingCrops {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 64)
        } else if let cropLoadError = viewModel.cropLoadError {
            VStack(spacing: Spacing.sm) {
                Text(cropLoadError)
                    .font(AppTypography.bodyMedium.font)
                    .foregroundStyle(Color.Text.subtle)
                Button("다시 시도") {
                    Task { await viewModel.loadCropsIfNeeded() }
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

    private var topAppBar: some View {
        HStack {
            Button {
                viewModel.goBack()
            } label: {
                AppIconView(source: .asset("chevron_backward"), size: 24)
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)

            Spacer()
        }
        .frame(height: 60)
        .padding(.horizontal, 4)
        .background(Color.Background.default)
    }

    private var titleHeader: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("재배 중인 작물 설정하기")
                .appTypography(.headlineMediumEmphasized)
                .foregroundStyle(Color.Text.default)

            Text("대표 재배지의 작물을 입력해주세요.\n작물은 최대 5개까지 선택 가능합니다.")
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

    @ViewBuilder
    private var categoryTabs: some View {
        if viewModel.cropCategories.isEmpty {
            Text("카테고리를 불러오는 중...")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(height: 56)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
        } else {
            AppTabBar(
                titles: viewModel.cropCategories.map(\.label),
                selection: selectedCategoryBinding,
                scrollable: true
            )
            .frame(height: 56)
        }
    }

    private func cropRow(_ crop: Crop) -> some View {
        let isSelected = viewModel.draft.cropIDs.contains(crop.id)
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

            OnboardingCTAButton(title: "완료", isVisuallyEnabled: selectedCount > 0) {
                guard selectedCount > 0 else { return }
                viewModel.goNext()
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
        AppChip(label: crop.name, isSelected: true, style: .solidPastel, trailingSystemImage: "xmark") {
            _ = viewModel.toggleCropSelection(crop.id)
            selectionLimitMessage = nil
        }
    }

    private func toggle(_ crop: Crop) {
        switch viewModel.toggleCropSelection(crop.id) {
        case .selected, .deselected:
            selectionLimitMessage = nil
        case .selectionLimitReached:
            selectionLimitMessage = "작물은 최대 5개까지 선택할 수 있어요."
        }
    }

    private struct SelectedCropChip: Identifiable {
        let id: UUID
        let name: String
    }
}

#if DEBUG
#Preview {
    CropSelectionView()
        .environment(OnboardingViewModel.preview())
}
#endif
