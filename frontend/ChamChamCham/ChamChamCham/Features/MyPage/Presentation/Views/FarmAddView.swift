//
//  FarmAddView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 밭 추가. 농지명 + 도로명 주소 검색(`AddressSearchSheet`, JUSO/V-World 엔진 재사용) + 작물 선택
/// (`CropPickerSheet` 재사용) → `POST /farms`. 온보딩 화면을 건드리지 않고 엔진/시트를 재사용한다.
struct FarmAddView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: FarmAddViewModel
    @State private var isShowingAddressSearch = false
    @State private var isShowingCropPicker = false
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
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "밭 추가",
                isDetail: true,
                leading: .init(.asset("chevron_backward")) { dismiss() }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    AppTextField(
                        label: "농지명",
                        placeholder: "농지명을 입력해주세요.",
                        text: $viewModel.farmName,
                        isRequired: true,
                        errorMessage: viewModel.nameError
                    )

                    fieldButton(
                        label: "재배지 도로명 주소",
                        isRequired: true,
                        value: viewModel.selectedAddressText,
                        placeholder: "도로명 주소를 검색해주세요.",
                        detail: viewModel.parcelSummary
                    ) {
                        isShowingAddressSearch = true
                    }

                    cropSection

                    if let message = viewModel.errorMessage {
                        Text(message)
                            .appTypography(.labelMedium)
                            .foregroundStyle(Color.Text.red)
                    }
                }
                .padding(.horizontal, Spacing.lg - Spacing.xs)
                .padding(.top, Spacing.md)
                .padding(.bottom, Spacing.xl)
            }
        }
        .background(Color.Background.default)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            AppButton("저장", variant: .secondary, size: .medium, fullWidth: true) {
                Task {
                    if await viewModel.save() {
                        onAdded()
                        dismiss()
                    }
                }
            }
            .disabled(!viewModel.canSave)
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.vertical, Spacing.md)
            .background(Color.Background.default)
        }
        .sheet(isPresented: $isShowingAddressSearch) {
            AddressSearchSheet(viewModel: viewModel.location) { address in
                Task { await viewModel.selectAddress(address) }
            }
        }
        .sheet(isPresented: $isShowingCropPicker) {
            CropPickerSheet(
                loadCrops: { await viewModel.loadCrops() },
                onComplete: { crops in viewModel.selectedCrops = crops }
            )
        }
    }

    @ViewBuilder private var cropSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("작물")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.default)

            Button {
                isShowingCropPicker = true
            } label: {
                Group {
                    if viewModel.selectedCrops.isEmpty {
                        HStack {
                            Text("작물을 선택해주세요.")
                                .appTypography(.bodyLarge)
                                .foregroundStyle(Color.Text.muted)
                            Spacer()
                            AppIconView(source: .asset("chevron_forward"), size: 24)
                                .foregroundStyle(Color.Icon.subtle)
                        }
                    } else {
                        HStack {
                            HStack(spacing: Spacing.sm) {
                                ForEach(viewModel.selectedCrops.prefix(3)) { crop in
                                    AppBadge(label: crop.name, style: .solidPastel, variant: .primary)
                                }
                                if viewModel.selectedCrops.count > 3 {
                                    Text("외 \(viewModel.selectedCrops.count - 3)종")
                                        .appTypography(.labelMedium)
                                        .foregroundStyle(Color.Text.subtle)
                                }
                            }
                            Spacer()
                            AppIconView(source: .asset("chevron_forward"), size: 24)
                                .foregroundStyle(Color.Icon.subtle)
                        }
                    }
                }
                .padding(.horizontal, Spacing.md)
                .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color.Object.subtle))
                .contentShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }

    private func fieldButton(
        label: String,
        isRequired: Bool,
        value: String?,
        placeholder: String,
        detail: String?,
        action: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: 2) {
                Text(label)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.default)
                if isRequired {
                    Text("*")
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.red)
                }
            }
            Button(action: action) {
                HStack {
                    Text(value ?? placeholder)
                        .appTypography(.bodyLarge)
                        .foregroundStyle(value == nil ? Color.Text.muted : Color.Text.default)
                        .lineLimit(1)
                    Spacer()
                    AppIconView(source: .asset("chevron_forward"), size: 24)
                        .foregroundStyle(Color.Icon.subtle)
                }
                .padding(.horizontal, Spacing.md)
                .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color.Object.subtle))
                .contentShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)

            if let detail {
                Text(detail)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
            }
        }
    }
}
