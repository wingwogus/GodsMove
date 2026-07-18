//
//  FarmListView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 프로필 수정 · 농업 정보 탭. 등록한 밭 카드 목록 + 삭제 모드 + 추가하기. `FarmCard`(DS) 재사용.
struct FarmListView: View {
    let container: DIContainer
    @State private var viewModel: FarmListViewModel
    @State private var isShowingAdd = false
    @State private var isConfirmingDelete = false

    /// Inline 농지명 edit (owned here so only one card edits at a time).
    @State private var editingFarmId: UUID?
    @State private var draftName = ""

    /// Crop-edit sheet target.
    @State private var cropEditFarm: StandaloneFarmResponseDTO?

    /// Address-edit sheet target + the location engine it drives (a fresh instance per farm).
    @State private var addressEditFarm: StandaloneFarmResponseDTO?
    @State private var addressEditLocation = FarmLocationViewModel()

    init(container: DIContainer) {
        self.container = container
        _viewModel = State(initialValue: FarmListViewModel(repository: container.makeFarmRepository()))
    }

    var body: some View {
        content
            .background(Color.Background.subtle)
            .safeAreaInset(edge: .bottom, spacing: 0) { deleteBar }
            .task { await viewModel.load() }
            .appToast(message: $viewModel.toastMessage)
            .fullScreenCover(isPresented: $isShowingAdd) {
                FarmAddView(
                    farmRepository: container.makeFarmRepository(),
                    cropCatalog: container.makeCropCatalogService()
                ) {
                    Task { await viewModel.load() }
                    viewModel.toastMessage = "농지 추가 완료되었습니다."
                }
            }
            .fullScreenCover(item: $cropEditFarm) { farm in
                CropPickerView(
                    loadCrops: { (try? await container.makeCropCatalogService().fetchCrops()) ?? [] },
                    loadCategories: { (try? await container.makeCropCatalogService().fetchCategories()) ?? [] },
                    initialSelection: farm.crops.map {
                        Crop(id: $0.id, name: $0.name, categoryCode: $0.usePartCategory, categoryLabel: $0.usePartCategoryLabel)
                    },
                    onComplete: { crops in
                        Task { await viewModel.updateCrops(farm, cropIds: crops.map(\.id)) }
                    }
                )
            }
            .fullScreenCover(item: $addressEditFarm) { farm in
                FarmLocationPickerView(
                    location: addressEditLocation,
                    showsFarmNameField: false,
                    headline: "재배지 주소 수정하기",
                    subtitle: "재배지의 주소명을 입력해주세요.",
                    ctaTitle: "저장",
                    onBack: { addressEditFarm = nil },
                    onPrimary: {
                        Task {
                            if await viewModel.updateLocation(farm, location: addressEditLocation) {
                                addressEditFarm = nil
                            }
                        }
                    }
                )
            }
    }

    /// 기존 주소로 재지오코딩해 지도 센터링 + 필지를 다시 조회한다(정밀 폴리곤 복원은 범위 밖 — 사용자가
    /// 필요하면 재검색/지도 재탭으로 갱신). 각 밭마다 새 `FarmLocationViewModel`을 만들어 이전 편집 상태가
    /// 남지 않게 한다.
    private func beginAddressEdit(_ farm: StandaloneFarmResponseDTO) {
        let location = FarmLocationViewModel()
        addressEditLocation = location
        addressEditFarm = farm
        Task {
            await location.selectAddress(
                JusoAddress(roadAddrPart1: farm.roadAddress, jibunAddr: farm.jibunAddress ?? "", bdNm: "")
            )
        }
    }

    @ViewBuilder private var content: some View {
        if viewModel.isLoading && viewModel.farms.isEmpty {
            LoadingView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = viewModel.errorMessage, viewModel.farms.isEmpty {
            VStack(spacing: Spacing.md) {
                Text(error)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .multilineTextAlignment(.center)
                Button("다시 시도") { Task { await viewModel.load() } }
                    .appTypography(.bodyMediumEmphasized)
                    .foregroundStyle(Color.Text.primary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(Spacing.lg)
        } else {
            ScrollView {
                LazyVStack(spacing: Spacing.md) {
                    header
                    ForEach(viewModel.farms, id: \.farmId) { farm in
                        FarmCard(
                            farmName: farm.name,
                            roadAddress: farm.roadAddress,
                            crops: farm.crops.map(\.name),
                            isSelected: viewModel.isSelected(farm.farmId),
                            isEditingName: editingFarmId == farm.farmId,
                            editingName: editingFarmId == farm.farmId ? $draftName : nil,
                            onCommitName: {
                                let name = draftName
                                editingFarmId = nil
                                Task { await viewModel.renameFarm(farm, to: name) }
                            },
                            onEditName: viewModel.isDeleting ? nil : {
                                draftName = farm.name
                                editingFarmId = farm.farmId
                            },
                            onTapAddress: viewModel.isDeleting ? nil : { beginAddressEdit(farm) },
                            onTapCrops: viewModel.isDeleting ? nil : { cropEditFarm = farm }
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if viewModel.isDeleting { viewModel.toggleSelection(farm.farmId) }
                        }
                    }
                    if !viewModel.isDeleting {
                        addButton
                    }
                }
                .padding(.horizontal, Spacing.lg - Spacing.xs)
                .padding(.vertical, Spacing.md)
            }
        }
    }

    private var header: some View {
        HStack(spacing: Spacing.sm) {
            Text("등록한 밭")
                .appTypography(.titleLargeEmphasized)
                .foregroundStyle(Color.Text.default)
            Text("\(viewModel.count)")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.inverse)
                .frame(minWidth: 28, minHeight: 28)
                .background(Circle().fill(Color.Object.primary))
            Spacer()
            if !viewModel.farms.isEmpty {
                Button(viewModel.isDeleting ? "취소" : "삭제하기") {
                    viewModel.toggleDeleteMode()
                }
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.subtle)
            }
        }
    }

    private var addButton: some View {
        Button {
            isShowingAdd = true
        } label: {
            Text("추가하기")
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.subtle)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.Object.default))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.Border.subtle, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var deleteBar: some View {
        if viewModel.isDeleting && !viewModel.selectedForDeletion.isEmpty {
            AppButton("선택 삭제 (\(viewModel.selectedForDeletion.count))", variant: .secondary, size: .medium, fullWidth: true) {
                isConfirmingDelete = true
            }
            .disabled(viewModel.isProcessingDelete)
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.vertical, Spacing.md)
            .background(Color.Background.default)
            .confirmationDialog("선택한 밭을 삭제할까요?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("삭제", role: .destructive) { Task { await viewModel.deleteSelected() } }
                Button("취소", role: .cancel) {}
            }
        }
    }
}
