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
    @State private var selectedCategory = "인기"

    private var filteredCrops: [Crop] {
        let byCategory = selectedCategory == "인기"
            ? viewModel.availableCrops
            : viewModel.availableCrops.filter { $0.category == selectedCategory }
        guard !searchText.trimmingCharacters(in: .whitespaces).isEmpty else { return byCategory }
        return byCategory.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }

    private var selectedCount: Int { viewModel.draft.cropIDs.count }

    var body: some View {
        @Bindable var viewModel = viewModel

        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Button {
                    viewModel.goBack()
                } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.appTextPrimary)
                }
                Spacer()
            }

            OnboardingProgressBar(currentStep: viewModel.currentStep)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("재배 작물을\n선택해주세요")
                    .font(.appTitle)

                HStack(spacing: Spacing.xs) {
                    Text("여러 개 선택 가능 · 나중에 변경할 수 있어요")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextSecondary)
                    Text("(필수 수집 데이터)")
                        .font(.appCaption)
                        .foregroundStyle(.red)
                }
            }

            searchField

            categoryChips

            Text("\(selectedCategory) 작물 · \(filteredCrops.count)개")
                .font(.appCaption)
                .foregroundStyle(Color.appTextSecondary)

            cropGrid

            PrimaryButton(title: selectedCount == 0 ? "작물을 선택하세요" : "다음") {
                viewModel.goNext()
            }
            .disabled(selectedCount == 0)
            .opacity(selectedCount == 0 ? 0.5 : 1)
        }
        .padding(Spacing.lg)
        .task {
            await viewModel.loadCropsIfNeeded()
        }
    }

    @ViewBuilder
    private var cropGrid: some View {
        if viewModel.isLoadingCrops {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
        } else if let cropLoadError = viewModel.cropLoadError {
            VStack(spacing: Spacing.sm) {
                Text(cropLoadError)
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextSecondary)
                Button("다시 시도") {
                    Task { await viewModel.loadCropsIfNeeded() }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
        } else {
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.sm), count: 4), spacing: Spacing.sm) {
                    ForEach(filteredCrops) { crop in
                        cropChip(crop)
                    }
                }
                .padding(.vertical, Spacing.sm)
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Color.appTextSecondary)
            TextField("작물 이름 검색...", text: $searchText)
        }
        .padding(Spacing.sm)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(viewModel.cropCategoryLabels, id: \.self) { category in
                    categoryChip(category)
                }
            }
        }
    }

    private func categoryChip(_ category: String) -> some View {
        let isSelected = category == selectedCategory
        return Button {
            selectedCategory = category
        } label: {
            HStack(spacing: Spacing.xs) {
                if category == "인기" {
                    Image(systemName: "star.fill")
                }
                Text(category)
            }
            .font(.appCaption)
            .foregroundStyle(isSelected ? Color.appBackground : Color.appTextPrimary)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(isSelected ? Color.appTextPrimary : Color.clear)
            .clipShape(Capsule())
            .overlay {
                if !isSelected {
                    Capsule().stroke(Color(.systemGray4))
                }
            }
        }
    }

    private func cropChip(_ crop: Crop) -> some View {
        let isSelected = viewModel.draft.cropIDs.contains(crop.id)
        return Button {
            toggle(crop)
        } label: {
            HStack(spacing: Spacing.xs) {
                Circle()
                    .fill(isSelected ? Color.appPrimary : Color.appPrimary.opacity(0.15))
                    .frame(width: 24, height: 24)
                Text(crop.name)
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextPrimary)
                    .lineLimit(1)
            }
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .overlay {
                Capsule().stroke(isSelected ? Color.appPrimary : Color(.systemGray4))
            }
        }
    }

    private func toggle(_ crop: Crop) {
        if let index = viewModel.draft.cropIDs.firstIndex(of: crop.id) {
            viewModel.draft.cropIDs.remove(at: index)
        } else {
            viewModel.draft.cropIDs.append(crop.id)
        }
    }
}

#if DEBUG
#Preview {
    CropSelectionView()
        .environment(OnboardingViewModel.preview())
}
#endif
