//
//  CropSelectionView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

/// Onboarding crop step. Wraps the shared `CropSelectionBody` with the onboarding chrome (back bar +
/// progress) and binds it to the onboarding draft.
struct CropSelectionView: View {
    @Environment(OnboardingViewModel.self) private var viewModel

    var body: some View {
        VStack(spacing: 0) {
            topAppBar

            OnboardingProgressBar(currentStep: viewModel.currentStep)
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 32)

            CropSelectionBody(
                title: "재배 중인 작물 설정하기",
                subtitle: "대표 재배지의 작물을 입력해주세요.\n작물은 최대 5개까지 선택 가능합니다.",
                crops: viewModel.availableCrops,
                categories: viewModel.cropCategories,
                isLoading: viewModel.isLoadingCrops,
                loadError: viewModel.cropLoadError,
                onRetry: { Task { await viewModel.loadCropsIfNeeded() } },
                selectedCropIDs: viewModel.draft.cropIDs,
                selectionLimit: 5,
                onToggle: { cropID in
                    switch viewModel.toggleCropSelection(cropID) {
                    case .selected, .deselected:
                        return .changed
                    case .selectionLimitReached:
                        return .limitReached
                    }
                },
                ctaTitle: "완료",
                onComplete: { viewModel.goNext() }
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.Background.default)
        .task {
            await viewModel.loadCropsIfNeeded()
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
}

#if DEBUG
#Preview {
    CropSelectionView()
        .environment(OnboardingViewModel.preview())
}
#endif
