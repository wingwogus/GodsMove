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
    @State private var isShowingMinimumFarmAlert = false

    init(container: DIContainer) {
        self.container = container
        _viewModel = State(initialValue: FarmListViewModel(repository: container.makeFarmRepository()))
    }

    var body: some View {
        content
            .background(Color.Background.subtle)
            .safeAreaInset(edge: .bottom, spacing: 0) { deleteBar }
            .task { await viewModel.load() }
            .fullScreenCover(isPresented: $isShowingAdd) {
                FarmAddView(
                    farmRepository: container.makeFarmRepository(),
                    cropCatalog: container.makeCropCatalogService()
                ) {
                    Task { await viewModel.load() }
                }
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
                            isSelected: viewModel.isSelected(farm.farmId)
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
                if viewModel.selectedForDeletion.count >= viewModel.farms.count {
                    isShowingMinimumFarmAlert = true
                } else {
                    isConfirmingDelete = true
                }
            }
            .disabled(viewModel.isProcessingDelete)
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.vertical, Spacing.md)
            .background(Color.Background.default)
            .confirmationDialog("선택한 밭을 삭제할까요?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("삭제", role: .destructive) { Task { await viewModel.deleteSelected() } }
                Button("취소", role: .cancel) {}
            }
            .alert("삭제할 수 없어요", isPresented: $isShowingMinimumFarmAlert) {
                Button("확인", role: .cancel) {}
            } message: {
                Text("최소 1개의 농지는 등록되어 있어야 해요.")
            }
        }
    }
}
