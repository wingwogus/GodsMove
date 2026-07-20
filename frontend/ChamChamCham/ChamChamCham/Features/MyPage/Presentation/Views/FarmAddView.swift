//
//  FarmAddView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 밭 추가. 온보딩 재배지 설정과 같은 2-step 흐름: step 1 `FarmLocationPickerView`(재배지 위치 + 농지명),
/// step 2 작물 설정(`CropSelectionBody` 재사용) → `POST /farms`. 온보딩 화면(`FarmLocationView`/
/// `CropSelectionView`)을 건드리지 않고 그 안의 재사용 가능한 조각만 가져다 쓴다. Step 전환은
/// `NavigationStack`의 view-local 상태로 관리하고, `FarmAddViewModel`은 데이터만 소유한다.
struct FarmAddView: View {
    private enum Step: Hashable {
        case crops
    }

    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: FarmAddViewModel
    @State private var path: [Step] = []
    var onAdded: () -> Void

    init(
        farmRepository: any FarmRepository,
        cropCatalog: any CropCatalogService,
        onAdded: @escaping () -> Void
    ) {
        _viewModel = State(
            initialValue: FarmAddViewModel(farmRepository: farmRepository, cropCatalog: cropCatalog)
        )
        self.onAdded = onAdded
    }

    var body: some View {
        NavigationStack(path: $path) {
            FarmLocationPickerView(
                location: viewModel.location,
                showsFarmNameField: true,
                farmName: $viewModel.farmName,
                headline: "대표 재배지 설정하기",
                subtitle: "재배지의 주소명과 농지명을 입력해주세요.",
                ctaTitle: "다음",
                onBack: { dismiss() },
                onPrimary: { path.append(.crops) }
            )
            .navigationDestination(for: Step.self) { _ in
                FarmAddCropStepView(viewModel: viewModel) {
                    Task {
                        if await viewModel.save() {
                            onAdded()
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

/// 밭 추가 step 2 (작물 설정). 온보딩 `CropSelectionView`가 `CropSelectionBody`를 감싸는 방식을 그대로
/// 따르되, 뒤로가기는 `NavigationStack`의 pop이고 완료는 `FarmAddViewModel.save()`를 호출한다.
private struct FarmAddCropStepView: View {
    @Bindable var viewModel: FarmAddViewModel
    var onComplete: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var crops: [Crop] = []
    @State private var categories: [CropCategory] = []
    @State private var isLoading = true

    var body: some View {
        VStack(spacing: 0) {
            topAppBar

            CropSelectionBody(
                title: "재배 중인 작물 설정하기",
                subtitle: "대표 재배지의 작물을 입력해주세요.",
                crops: crops,
                categories: categories,
                isLoading: isLoading,
                loadError: nil,
                onRetry: { Task { await load() } },
                selectedCropIDs: viewModel.selectedCrops.map(\.id),
                selectionLimit: nil,
                onToggle: toggle,
                ctaTitle: "완료",
                onComplete: onComplete
            )

            if let message = viewModel.errorMessage {
                Text(message)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.red)
                    .padding(.horizontal, Spacing.lg - Spacing.xs)
                    .padding(.top, Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.Background.default)
        .toolbar(.hidden, for: .navigationBar)
        .task { await load() }
    }

    private var topAppBar: some View {
        AppTopAppBar(
            title: "",
            isDetail: true,
            showBorder: false,
            leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
        )
    }

    private func load() async {
        isLoading = true
        crops = await viewModel.loadCrops()
        categories = await viewModel.loadCategories()
        isLoading = false
    }

    private func toggle(_ cropID: UUID) -> CropSelectionBody.ToggleOutcome {
        if let index = viewModel.selectedCrops.firstIndex(where: { $0.id == cropID }) {
            viewModel.selectedCrops.remove(at: index)
            return .changed
        }
        guard let crop = crops.first(where: { $0.id == cropID }) else { return .changed }
        viewModel.selectedCrops.append(crop)
        return .changed
    }
}
