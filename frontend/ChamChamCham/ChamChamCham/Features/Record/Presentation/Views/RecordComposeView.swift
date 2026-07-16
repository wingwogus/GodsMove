//
//  RecordComposeView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI
import PhotosUI

/// 텍스트 기록 작성(생성) 화면 (Figma `텍스트로 기록하기`). 공통 필드 + workType별 동적 상세 +
/// 입력 검증 + 사진 첨부. origin-dev `SaveRecordRequest` 계약 대상.
struct RecordComposeView: View {
    @State private var viewModel: RecordComposeViewModel
    @State private var showPesticidePicker = false
    @State private var photoItem: PhotosPickerItem?
    @Environment(\.dismiss) private var dismiss
    private let onComplete: (UUID) -> Void

    init(
        repository: any RecordRepository,
        mediaUpload: any MediaUploadRepository,
        onComplete: @escaping (UUID) -> Void
    ) {
        _viewModel = State(initialValue: RecordComposeViewModel(repository: repository, mediaUpload: mediaUpload))
        self.onComplete = onComplete
    }

    private var vm: RecordComposeViewModel { viewModel }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "기록하기",
                isDetail: true,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
            )
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    basicSection
                    Divider().overlay(Color.Border.subtle)
                    workSection
                    Divider().overlay(Color.Border.subtle)
                    photoSection
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .scrollDismissesKeyboard(.interactively)
            bottomButton
        }
        .dismissKeyboardOnTap()
        .navigationBarHidden(true)
        .task { await vm.onAppear() }
        .task(id: photoItem) {
            if let photoItem, let data = try? await photoItem.loadTransferable(type: Data.self) {
                await vm.addImage(data)
                self.photoItem = nil
            }
        }
        .sheet(isPresented: $showPesticidePicker) {
            PesticidePickerSheet(search: vm.searchPesticides, current: vm.selectedPesticide) { pesticide in
                Task { await vm.selectPesticide(pesticide) }
            }
            .presentationDragIndicator(.visible)
        }
        .onChange(of: vm.createdRecordId) { _, newValue in
            if let newValue { onComplete(newValue); dismiss() }
        }
    }

    // MARK: - 기본 정보 + 진행 작물

    private var basicSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            // 기본 정보: 날짜 + 날씨(자동)
            VStack(alignment: .leading, spacing: 8) {
                fieldLabel("기본 정보", required: true)
                HStack(alignment: .top, spacing: 8) {
                    AppDateField(selection: Binding(get: { vm.date }, set: { vm.date = $0 ?? Date() }))
                    weatherBox
                }
                if vm.showValidation, let error = vm.weatherError {
                    errorLine(error)
                }
            }

            // 진행 작물: 농지 + 작물
            VStack(alignment: .leading, spacing: 8) {
                fieldLabel("진행 작물", required: true)
                HStack(alignment: .top, spacing: 8) {
                    AppDropdown(
                        placeholder: "농지 선택",
                        options: vm.farms,
                        selection: Binding(
                            get: { vm.farms.first { $0.farmId == vm.selectedFarmId } },
                            set: { vm.selectedFarmId = $0?.farmId }
                        ),
                        errorMessage: (vm.showValidation && vm.selectedFarmId == nil) ? "" : nil,
                        optionTitle: { $0.farmName }
                    )
                    AppDropdown(
                        placeholder: "작물 선택",
                        options: vm.crops,
                        selection: Binding(
                            get: { vm.crops.first { $0.id == vm.selectedCropId } },
                            set: { vm.selectedCropId = $0?.id }
                        ),
                        errorMessage: (vm.showValidation && vm.selectedCropId == nil) ? "" : nil,
                        optionTitle: { $0.name }
                    )
                }
                if vm.showValidation, let error = vm.farmCropError {
                    errorLine(error)
                }
            }
        }
    }

    private var weatherBox: some View {
        HStack(spacing: 4) {
            if vm.isLoadingWeather {
                ProgressView()
            } else if let weather = vm.weather {
                Text(weather.condition)
                Text("\(weather.temperature)°")
            } else if vm.weatherLoadFailed {
                AppIconView(source: .asset("refresh"), size: 16)
                Text("다시 시도")
            } else {
                Text("날씨").foregroundStyle(Color.Text.muted)
            }
        }
        .appTypography(.bodyLarge)
        .foregroundStyle(vm.weatherLoadFailed ? Color.Text.red : Color.Text.subtle)
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .padding(.horizontal, 16)
        .background(Color.Object.secondarySubtle)
        .overlay(RoundedRectangle(cornerRadius: 8)
            .stroke(vm.weatherLoadFailed ? Color.Border.error : Color.Border.default, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .contentShape(Rectangle())
        .onTapGesture {
            if vm.weatherLoadFailed { Task { await vm.retryWeather() } }
        }
    }

    // MARK: - 작업 내용 + 상세

    private var workSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            AppDropdown(
                "작업 내용",
                placeholder: "진행한 작업을 선택해주세요.",
                options: WorkType.allCases,
                selection: $viewModel.workType,
                isRequired: true,
                errorMessage: (vm.showValidation && vm.workType == nil) ? "진행 작업을 선택해주세요." : nil,
                optionTitle: { $0.label }
            )
            AppTextEditor(
                placeholder: "작업 내용을 30자 이상 작성해주세요.",
                text: $viewModel.memo,
                errorMessage: vm.showValidation ? vm.memoError : nil,
                characterLimit: 500
            )
            .frame(height: 200)

            detailSection
        }
    }

    @ViewBuilder private var detailSection: some View {
        switch vm.workType {
        case .planting: plantingDetail
        case .watering: wateringDetail
        case .fertilizing: fertilizingDetail
        case .pestControl: pestControlDetail
        case .weeding: weedingDetail
        case .harvest: harvestDetail
        case .pruning, .etc, .none: EmptyView()
        }
    }

    // MARK: 심기
    private var plantingDetail: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                fieldLabel("심은 방법", required: true)
                choiceRow(PlantingMethod.allCases, selection: $viewModel.plantingMethod) { $0.label }
                if let error = requiredError(vm.plantingMethod == nil, "심은 방법을 선택해주세요.") {
                    errorLine(error)
                }
            }
            if vm.plantingMethod == .seed {
                numberField("심은 씨앗량 (g)", placeholder: "뿌린 씨앗의 양을 작성해주세요.",
                            text: $viewModel.seedAmount, required: true,
                            error: requiredError(Double(vm.seedAmount.trimmingCharacters(in: .whitespaces)) == nil))
            } else if vm.plantingMethod == .seedling {
                numberField("심은 갯수 (주)", placeholder: "심은 모종의 갯수를 작성해주세요.",
                            text: $viewModel.seedlingCount, required: true,
                            error: requiredError(Int(vm.seedlingCount.trimmingCharacters(in: .whitespaces)) == nil))
                AppDropdown("모종 번식법", placeholder: "진행한 모종 번식법을 선택해주세요.",
                            options: PropagationMethod.allCases, selection: $viewModel.propagationMethod,
                            optionTitle: { $0.label })
            }
        }
    }

    // MARK: 물주기
    private var wateringDetail: some View {
        VStack(alignment: .leading, spacing: 16) {
            AppDropdown("진행 방식", placeholder: "물주기 진행 방식을 선택해주세요.",
                        options: IrrigationMethod.allCases, selection: $viewModel.irrigationMethod,
                        isRequired: true, optionTitle: { $0.label })
            AppDropdown("물의 양", placeholder: "진행한 물의 양 정도를 선택해주세요.",
                        options: IrrigationAmount.allCases, selection: $viewModel.irrigationAmount,
                        isRequired: true, optionTitle: { $0.label })
        }
    }

    // MARK: 비료 주기
    private var fertilizingDetail: some View {
        VStack(alignment: .leading, spacing: 16) {
            AppTextField(label: "사용 비료", placeholder: "사용한 비료를 작성해주세요.",
                         text: $viewModel.fertilizerMaterialName, isRequired: true,
                         errorMessage: requiredError(
                            vm.fertilizerMaterialName.trimmingCharacters(in: .whitespaces).isEmpty))
            HStack(alignment: .top, spacing: 8) {
                numberField("비료 사용량", placeholder: "비료 사용량 작성",
                            text: $viewModel.fertilizerAmount, required: true,
                            error: requiredError(Double(vm.fertilizerAmount.trimmingCharacters(in: .whitespaces)) == nil))
                unitDropdown(FertilizerAmountUnit.allCases, selection: $viewModel.fertilizerAmountUnit) { $0.label }
            }
            AppDropdown("진행 방식", placeholder: "비료 주기 진행 방식을 선택해주세요.",
                        options: FertilizingMethod.allCases, selection: $viewModel.fertilizingMethod,
                        isRequired: true, optionTitle: { $0.label })
        }
    }

    // MARK: 병해충 관리
    private var pestControlDetail: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                fieldLabel("사용 농약", required: true)
                let pesticideMissing = requiredError(vm.selectedPesticide == nil, "사용 농약을 선택해주세요.")
                Button { showPesticidePicker = true } label: {
                    HStack {
                        Text(vm.selectedPesticide?.itemName ?? "사용한 농약을 선택해주세요.")
                            .foregroundStyle(vm.selectedPesticide == nil ? Color.Text.muted : Color.Text.default)
                        Spacer()
                        AppIconView(source: .asset("keyboard_arrow_down"), size: 16)
                            .foregroundStyle(Color.Icon.subtle)
                    }
                    .appTypography(.bodyLarge)
                    .frame(minHeight: 56).padding(.horizontal, 16)
                    .overlay(RoundedRectangle(cornerRadius: 8)
                        .stroke(pesticideMissing == nil ? Color.Border.default : Color.Border.error, lineWidth: 1))
                }
                .buttonStyle(.plain)
                if let pesticideMissing { errorLine(pesticideMissing) }
            }
            HStack(alignment: .top, spacing: 8) {
                numberField("농약 사용량", placeholder: "농약 사용량 작성",
                            text: $viewModel.pesticideAmount, required: true,
                            error: requiredError(Double(vm.pesticideAmount.trimmingCharacters(in: .whitespaces)) == nil))
                unitDropdown(PesticideAmountUnit.allCases, selection: $viewModel.pesticideAmountUnit) { $0.label }
            }
            numberField("총 살포량 (L)", placeholder: "총 농약 살포량을 작성해주세요.",
                        text: $viewModel.totalSprayAmount, required: true,
                        error: requiredError(Double(vm.totalSprayAmount.trimmingCharacters(in: .whitespaces)) == nil))
            AppDropdown("대상 병해충", placeholder: "대상 병해충을 선택해주세요.",
                        options: vm.pests, selection: $viewModel.selectedPest, optionTitle: { $0.name })
        }
    }

    // MARK: 잡초 관리
    private var weedingDetail: some View {
        AppDropdown("진행 방식", placeholder: "진행한 잡초 관리 방식을 선택해주세요.",
                    options: WeedingMethod.allCases, selection: $viewModel.weedingMethod,
                    isRequired: true, optionTitle: { $0.label })
    }

    // MARK: 수확
    private var harvestDetail: some View {
        VStack(alignment: .leading, spacing: 16) {
            numberField("재배 기간 (개월)", placeholder: "작물의 총 재배 기간을 작성해주세요.",
                        text: $viewModel.growthPeriod, required: true)
            VStack(alignment: .leading, spacing: 8) {
                numberField("수확량 (kg)", placeholder: "작물의 총 수확량을 작성해주세요.",
                            text: $viewModel.harvestAmount, required: true)
                Button { vm.harvestAmountUnknown.toggle() } label: {
                    HStack(spacing: 8) {
                        Image(systemName: vm.harvestAmountUnknown ? "checkmark.square.fill" : "square")
                            .foregroundStyle(vm.harvestAmountUnknown ? Color.Icon.primary : Color.Icon.subtle)
                        Text("잘 모르겠음").appTypography(.bodyMedium).foregroundStyle(Color.Text.subtle)
                    }
                }
                .buttonStyle(.plain)
            }
            AppDropdown("수확 부위", placeholder: "작물의 수확 부위를 선택해주세요.",
                        options: MedicinalPart.allCases, selection: $viewModel.medicinalPart,
                        optionTitle: { $0.label })
            HStack {
                Text("최종 수확 완료").appTypography(.bodyMedium).foregroundStyle(Color.Text.default)
                Spacer()
                AppToggle(isOn: $viewModel.isLastHarvest)
            }
        }
    }

    // MARK: - 사진

    private var photoSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("사진 첨부하기").appTypography(.bodyMediumEmphasized).foregroundStyle(Color.Text.default)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(vm.mediaIds.enumerated()), id: \.offset) { index, _ in
                        AppImageUploadSlot(label: "", onRemove: { vm.removeImage(at: index) }) {
                            RoundedRectangle(cornerRadius: 8).fill(Color.Object.muted)
                                .overlay(
                                    AppIconView(source: .asset("photo"), size: 24)
                                        .foregroundStyle(Color.Icon.disabled)
                                )
                        }
                    }
                    if vm.canAddMorePhotos {
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            AppImagePlaceholderSlot(count: vm.mediaIds.count, isUploading: vm.isUploadingImage)
                        }
                    }
                }
            }
        }
    }

    // MARK: - 하단 완료

    private var bottomButton: some View {
        VStack(spacing: 0) {
            if let error = vm.errorMessage {
                errorLine(error).padding(.horizontal, 20)
            }
            AppButton("완료", variant: .secondary, size: .large, fullWidth: true,
                      appearsDisabled: !vm.canSubmit) {
                Task { await vm.submit() }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .background(Color.Background.default)
        .overlay(alignment: .top) { Rectangle().fill(Color.Border.subtle).frame(height: 1) }
    }

    // MARK: - Helpers

    private func fieldLabel(_ text: String, required: Bool) -> some View {
        HStack(spacing: 2) {
            Text(text).appTypography(.bodyMedium).foregroundStyle(Color.Text.default)
            if required { Text("*").appTypography(.bodyMedium).foregroundStyle(Color.Text.red) }
        }
    }

    private func errorLine(_ text: String) -> some View {
        Text(text).appTypography(.labelMedium).foregroundStyle(Color.Text.red)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func numberField(_ label: String, placeholder: String, text: Binding<String>,
                             required: Bool, error: String? = nil) -> some View {
        AppTextField(label: label, placeholder: placeholder, text: text,
                     isRequired: required, errorMessage: error, keyboardType: .decimalPad)
    }

    /// "필수로 입력해주세요." shown only after 완료 tap (`showValidation`) when `invalid`.
    private func requiredError(_ invalid: Bool, _ message: String = "필수로 입력해주세요.") -> String? {
        (vm.showValidation && invalid) ? message : nil
    }

    private func choiceRow<T: Hashable>(_ items: [T], selection: Binding<T?>, title: @escaping (T) -> String) -> some View {
        HStack(spacing: 8) {
            ForEach(items, id: \.self) { item in
                AppSelectItem(title: title(item), isSelected: selection.wrappedValue == item) {
                    selection.wrappedValue = item
                }
            }
        }
    }

    /// Passes a blank `label` so this reserves the same label-row height as the adjacent
    /// `numberField`'s "레이블 *" line — otherwise the field box sits higher and looks misaligned
    /// next to the labeled text field in the same `HStack`.
    private func unitDropdown<T: Hashable>(_ items: [T], selection: Binding<T>, title: @escaping (T) -> String) -> some View {
        AppDropdown(
            " ",
            placeholder: "단위 선택",
            options: items,
            selection: Binding(get: { selection.wrappedValue }, set: { if let v = $0 { selection.wrappedValue = v } }),
            optionTitle: title
        )
    }
}

/// 사진 추가 빈 슬롯 (0/5 카운터 + 카메라 아이콘).
private struct AppImagePlaceholderSlot: View {
    let count: Int
    let isUploading: Bool

    var body: some View {
        RoundedRectangle(cornerRadius: 8).fill(Color.Object.muted)
            .frame(width: 96, height: 96)
            .overlay {
                if isUploading {
                    ProgressView()
                } else {
                    VStack(spacing: 2) {
                        AppIconView(source: .asset("photo_camera"), size: 24)
                            .foregroundStyle(Color.Icon.subtle)
                        Text("\(count)/5").appTypography(.bodyMedium).foregroundStyle(Color.Text.subtle)
                    }
                }
            }
    }
}

/// 사용 농약 검색 바텀시트 (`GET /pesticides?keyword`). 목록에서 고른 항목은 체크 표시로만
/// 반영되고, 실제 선택은 하단 "완료" 버튼으로 확정한다 (Figma `bottom-sheet` 1500:7963).
private struct PesticidePickerSheet: View {
    let search: (String?) async -> [Pesticide]
    var current: Pesticide?
    let onSelect: (Pesticide) -> Void

    @State private var keyword = ""
    @State private var results: [Pesticide] = []
    @State private var selection: Pesticide?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 16) {
                Text("사용 농약").appTypography(.titleMediumEmphasized).foregroundStyle(Color.Text.default)
                AppSearchBar(text: $keyword, placeholder: "사용한 농약을 검색해보세요.")
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)

            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Array(results.enumerated()), id: \.element.id) { index, pesticide in
                        row(for: pesticide)
                        if index < results.count - 1 {
                            Divider().background(Color.Border.default).padding(.leading, 20)
                        }
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color.Background.default)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            AppButton("완료", variant: .secondary, size: .medium, fullWidth: true) {
                guard let selection else { return }
                onSelect(selection)
                dismiss()
            }
            .disabled(selection == nil)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.Background.default)
        }
        .dismissKeyboardOnTap()
        .task {
            selection = current
            results = await search(nil)
        }
        .task(id: keyword) { results = await search(keyword) }
    }

    private func row(for pesticide: Pesticide) -> some View {
        let isSelected = pesticide == selection
        return Button {
            selection = pesticide
        } label: {
            HStack(spacing: Spacing.md) {
                Text(pesticide.itemName)
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
}
