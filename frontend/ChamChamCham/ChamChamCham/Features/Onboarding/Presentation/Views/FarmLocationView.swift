//
//  FarmLocationView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct FarmLocationView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @State private var farmLocationViewModel = FarmLocationViewModel()
    @State private var isSearchSheetPresented = false
    @State private var hasAttemptedNext = false

    private var isValid: Bool {
        farmLocationViewModel.requiredInputError(farmName: viewModel.draft.farmName) == nil
            && farmLocationViewModel.canProceed
    }

    private var requiredInputError: String? {
        guard hasAttemptedNext else { return nil }
        return farmLocationViewModel.requiredInputError(farmName: viewModel.draft.farmName)
    }

    var body: some View {
        @Bindable var viewModel = viewModel

        VStack(spacing: 0) {
            topAppBar

            OnboardingProgressBar(currentStep: viewModel.currentStep)
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 36)

            header
                .padding(.horizontal, 20)
                .padding(.bottom, 20)

            mapOverlaySection
                .ignoresSafeArea(.container, edges: .bottom)
        }
        .background(Color.Background.default)
        // 농지명 필드에 키보드 툴바로 "완료"를 달아 탭-바깥 없이도 키보드를 닫을 수 있게 한다.
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("완료") {
                    UIApplication.shared.sendAction(
                        #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
                    )
                }
            }
        }
        .overlay(alignment: .bottom) {
            bottomCTA
        }
        // 농지명 입력 시 키보드가 하단 CTA를 밀어 올리지 않도록 고정한다 (SearchView와 동일 패턴).
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .sheet(isPresented: $isSearchSheetPresented) {
            AddressSearchSheet(viewModel: farmLocationViewModel) { address in
                Task { await farmLocationViewModel.selectAddress(address) }
            }
        }
    }

    private var topAppBar: some View {
        AppTopAppBar(
            title: "",
            isDetail: true,
            showBorder: false,
            leading: .init(.asset("arrow_back_ios_new")) { viewModel.goBack() }
        )
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("대표 재배지 설정하기")
                .appTypography(.headlineMedium)
                .foregroundStyle(Color.Text.default)

            Text("재배지의 주소명과 농지명을 입력해주세요.")
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.muted)

            Text("정확한 재배지 위치로 날씨·병해충 등 맞춤 영농 정보를 제공하기 위해 필요해요.")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var mapOverlaySection: some View {
        ZStack(alignment: .top) {
            // 상단 오버레이 필드(주소/농지명) 높이만큼 컨트롤을 아래로 밀어 겹침을 피한다.
            FarmLocationMapSection(viewModel: farmLocationViewModel, controlsTopInset: 160, bottomInset: 116)

            VStack(spacing: 12) {
                addressOverlayField
                farmNameOverlayField

                if let requiredInputError {
                    Text(requiredInputError)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
        }
        .frame(maxWidth: .infinity)
        .frame(maxHeight: .infinity)
    }

    private var addressOverlayField: some View {
        Button {
            isSearchSheetPresented = true
        } label: {
            HStack(spacing: 12) {
                AppIconView(source: .asset("search"), size: 22)
                    .foregroundStyle(Color.Icon.default)
                Text(displayedAddressText ?? "주소지를 입력해주세요.")
                    .appTypography(.bodyLarge)
                    .foregroundStyle(displayedAddressText == nil ? Color.Text.muted : Color.Text.default)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(height: 56)
            .background(Color.Background.default)
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(addressBorderColor, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }

    /// 도로명이 없는 농지(지적도에 도로명 미부여)는 지번 주소로 표시한다. 둘 다 없으면 nil(미입력).
    private var displayedAddressText: String? {
        guard let address = farmLocationViewModel.selectedAddress else { return nil }
        if !address.roadAddrPart1.isEmpty { return address.roadAddrPart1 }
        return address.jibunAddr.isEmpty ? nil : address.jibunAddr
    }

    private var farmNameOverlayField: some View {
        TextField("농지명을 입력해주세요.", text: Binding(
            get: { viewModel.draft.farmName },
            set: { viewModel.draft.farmName = $0 }
        ))
        .appTypography(.bodyLarge)
        .foregroundStyle(Color.Text.default)
        .textInputAutocapitalization(.never)
        .padding(.horizontal, 16)
        .frame(height: 56)
        .background(Color.Background.default)
        .overlay {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(farmNameBorderColor, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var addressBorderColor: Color {
        hasAttemptedNext && farmLocationViewModel.selectedAddress == nil ? Color.Border.error : Color.Border.default
    }

    private var farmNameBorderColor: Color {
        hasAttemptedNext && viewModel.draft.farmName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.Border.error : Color.Border.default
    }

    private var bottomCTA: some View {
        VStack(spacing: 0) {
            OnboardingCTAButton(title: "다음", isVisuallyEnabled: isValid) {
                hasAttemptedNext = true
                guard isValid else { return }
                applySelectionToDraft()
                viewModel.goNext()
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 28)
        }
    }

    private func applySelectionToDraft() {
        guard let address = farmLocationViewModel.selectedAddress else { return }
        viewModel.draft.farmRoadAddress = address.roadAddrPart1
        viewModel.draft.farmJibunAddress = address.jibunAddr
        viewModel.draft.farmLatitude = farmLocationViewModel.submissionCoordinate?.latitude
        viewModel.draft.farmLongitude = farmLocationViewModel.submissionCoordinate?.longitude
        viewModel.draft.farmPNU = farmLocationViewModel.submissionPNU
        viewModel.draft.farmLandCategory = farmLocationViewModel.submissionLandCategory
        viewModel.draft.farmAreaSqm = farmLocationViewModel.submissionAreaSqm
        viewModel.draft.farmAreaIsManualEntry = farmLocationViewModel.submissionAreaIsManualEntry
        viewModel.draft.farmBoundaryCoordinates = farmLocationViewModel.submissionBoundaryCoordinates
    }
}

#if DEBUG
#Preview {
    FarmLocationView()
        .environment(OnboardingViewModel.preview())
}
#endif
