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

    private var selectedCropChips: [SelectedCropChip] {
        viewModel.draft.cropIDs.map { cropID in
            let cropName = viewModel.availableCrops.first(where: { $0.id == cropID })?.name ?? "선택 작물"
            return SelectedCropChip(id: cropID, name: cropName)
        }
    }

    private let chipColumns = [
        GridItem(.adaptive(minimum: 92), spacing: Spacing.sm, alignment: .leading)
    ]

    var body: some View {
        VStack(spacing: 0) {
            topAppBar

            OnboardingProgressBar(currentStep: viewModel.currentStep)
                .padding(.horizontal, 20)
                .padding(.bottom, Spacing.md)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    titleHeader
                        .padding(.horizontal, 20)
                        .padding(.bottom, Spacing.md)

                    Section {
                        cropList
                            .padding(.bottom, selectedCount == 0 ? 132 : 224)
                    } header: {
                        stickyControls
                    }
                }
            }
        }
        .background(Color.Background.default)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            bottomTray
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
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 28, weight: .medium))
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
                ForEach(filteredCrops) { crop in
                    cropRow(crop)
                    Divider()
                        .background(Color.Border.subtle)
                        .padding(.leading, 20)
                }
            }
        }
    }

    private var topAppBar: some View {
        HStack {
            Button {
                viewModel.goBack()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
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
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("재배 중인 작물 설정하기")
                .font(AppTypography.titleLargeEmphasized.font)
                .foregroundStyle(Color.Text.default)

            Text("대표 재배지의 작물을 입력해주세요.\n작물은 최대 5개까지 선택 가능합니다.")
                .font(AppTypography.bodyMedium.font)
                .foregroundStyle(Color.Text.subtle)
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
        HStack(spacing: Spacing.sm) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(Color.Icon.default)
            TextField("작물명을 입력해주세요.", text: $searchText)
                .font(AppTypography.bodyMedium.font)
                .textInputAutocapitalization(.never)
        }
        .padding(.horizontal, Spacing.md)
        .frame(height: 56)
        .background(Color.Object.default)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.Border.subtle, lineWidth: 1)
        }
    }

    @ViewBuilder
    private var categoryTabs: some View {
        if viewModel.cropCategories.isEmpty {
            Text("카테고리를 불러오는 중...")
                .font(AppTypography.labelMedium.font)
                .foregroundStyle(Color.Text.muted)
                .frame(height: 56)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(viewModel.cropCategories) { category in
                        categoryTab(category)
                    }
                }
                .padding(.horizontal, 10)
            }
            .frame(height: 56)
        }
    }

    private func categoryTab(_ category: CropCategory) -> some View {
        let isSelected = category.code == selectedCategory?.code
        return Button {
            selectedCategoryCode = category.code
            selectionLimitMessage = nil
        } label: {
            VStack(spacing: 0) {
                Text(category.label)
                    .font(AppTypography.labelMediumEmphasized.font)
                    .foregroundStyle(isSelected ? Color.Text.primary : Color.Text.subtle)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                Rectangle()
                    .fill(isSelected ? Color.Object.primary : Color.clear)
                    .frame(height: 2)
            }
            .frame(width: 104, height: 56)
        }
        .buttonStyle(.plain)
    }

    private func cropRow(_ crop: Crop) -> some View {
        let isSelected = viewModel.draft.cropIDs.contains(crop.id)
        return Button {
            toggle(crop)
        } label: {
            HStack(spacing: Spacing.md) {
                Text(crop.name)
                    .font(AppTypography.bodyLarge.font)
                    .foregroundStyle(isSelected ? Color.Text.primary : Color.Text.default)
                    .lineLimit(1)

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24, weight: .semibold))
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
            if selectedCropChips.isEmpty {
                Text("작물을 1개 이상 선택해주세요.")
                    .font(AppTypography.labelMedium.font)
                    .foregroundStyle(Color.Text.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                LazyVGrid(columns: chipColumns, alignment: .leading, spacing: Spacing.sm) {
                    ForEach(selectedCropChips) { crop in
                        selectedCropChip(crop)
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

            Button {
                guard selectedCount > 0 else { return }
                viewModel.goNext()
            } label: {
                Text("완료")
                    .font(AppTypography.bodyLarge.font)
                    .foregroundStyle(selectedCount == 0 ? Color.Text.muted : Color.Text.inverse)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(selectedCount == 0 ? Color.Object.disabled : Color.Object.bold)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .disabled(selectedCount == 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, selectedCropChips.isEmpty ? Spacing.md : Spacing.lg)
        .padding(.bottom, Spacing.md)
        .background {
            UnevenRoundedRectangle(cornerRadii: .init(topLeading: 32, topTrailing: 32))
                .fill(Color.Background.default)
                .shadow(color: .black.opacity(0.08), radius: 16, y: -4)
        }
    }

    private func selectedCropChip(_ crop: SelectedCropChip) -> some View {
        HStack(spacing: Spacing.xs) {
            Text(crop.name)
                .font(AppTypography.labelMedium.font)
                .foregroundStyle(Color.Text.primary)
                .lineLimit(1)

            Image(systemName: "xmark")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.Icon.primary)
        }
        .padding(.leading, Spacing.md)
        .padding(.trailing, Spacing.sm)
        .frame(height: 32)
        .background(Color.Object.primarySubtle)
        .clipShape(Capsule())
        .overlay {
            Capsule()
                .stroke(Color.Border.primary, lineWidth: 1)
        }
        .onTapGesture {
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
