//
//  FarmLocationPickerView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/18/26.
//

import SwiftUI

/// Reusable full-map 재배지 picker screen, shared by (a) 밭 추가 step 1 and (b) 주소 수정. Reuses the
/// onboarding-decoupled `FarmLocationMapSection` (map/parcel/drawing) and `AddressSearchSheet` (JUSO
/// search) exactly as `FarmAddView`/`FarmLocationView` already do, but without the onboarding chrome
/// (progress bar / `OnboardingViewModel`). Onboarding's own `FarmLocationView` is left untouched.
struct FarmLocationPickerView: View {
    @Bindable var location: FarmLocationViewModel
    /// Farm-add step 1 shows an inline 농지명 field; address-edit does not (name is edited on the card).
    var showsFarmNameField: Bool = false
    var farmName: Binding<String>? = nil

    var headline: String = "재배지 설정하기"
    var subtitle: String = "재배지의 주소명을 입력해주세요."
    var ctaTitle: String = "다음"

    var onBack: () -> Void
    var onPrimary: () -> Void

    @State private var isSearchSheetPresented = false
    @State private var hasAttemptedPrimary = false

    var body: some View {
        VStack(spacing: 0) {
            topAppBar

            header
                .padding(.horizontal, Spacing.lg - Spacing.xs)
                .padding(.bottom, Spacing.lg - Spacing.xs)

            mapOverlaySection
                .ignoresSafeArea(.container, edges: .bottom)
        }
        .background(Color.Background.default)
        .toolbar(.hidden, for: .navigationBar)
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
        .overlay(alignment: .bottom) { bottomCTA }
        // 농지명 입력 시 키보드가 하단 CTA를 밀어 올리지 않도록 고정한다 (SearchView와 동일 패턴).
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .sheet(isPresented: $isSearchSheetPresented) {
            AddressSearchSheet(viewModel: location) { address in
                Task { await location.selectAddress(address) }
            }
        }
    }

    private var topAppBar: some View {
        AppTopAppBar(
            title: "",
            isDetail: true,
            showBorder: false,
            leading: .init(.asset("arrow_back_ios_new"), action: onBack)
        )
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(headline)
                .appTypography(.headlineMedium)
                .foregroundStyle(Color.Text.default)
            Text(subtitle)
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var mapOverlaySection: some View {
        ZStack(alignment: .top) {
            // 상단 오버레이 필드(주소/농지명) 높이만큼 컨트롤을 아래로 밀어 겹침을 피한다.
            FarmLocationMapSection(
                viewModel: location,
                controlsTopInset: showsFarmNameField ? 160 : 100,
                bottomInset: 116
            )

            VStack(spacing: Spacing.sm) {
                addressOverlayField
                if showsFarmNameField, let farmName {
                    farmNameOverlayField(farmName)
                }

                if let validationMessage {
                    Text(validationMessage)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.top, Spacing.lg - Spacing.xs)
        }
        .frame(maxWidth: .infinity)
        .frame(maxHeight: .infinity)
    }

    private var addressOverlayField: some View {
        Button {
            isSearchSheetPresented = true
        } label: {
            HStack(spacing: Spacing.sm) {
                AppIconView(source: .asset("search"), size: 22)
                    .foregroundStyle(Color.Icon.default)
                Text(displayedAddressText ?? "주소지를 입력해주세요.")
                    .appTypography(.bodyLarge)
                    .foregroundStyle(displayedAddressText == nil ? Color.Text.muted : Color.Text.default)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
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
        guard let address = location.selectedAddress else { return nil }
        if !address.roadAddrPart1.isEmpty { return address.roadAddrPart1 }
        return address.jibunAddr.isEmpty ? nil : address.jibunAddr
    }

    private func farmNameOverlayField(_ farmName: Binding<String>) -> some View {
        TextField("농지명을 입력해주세요.", text: farmName)
            .appTypography(.bodyLarge)
            .foregroundStyle(Color.Text.default)
            .textInputAutocapitalization(.never)
            .padding(.horizontal, Spacing.md)
            .frame(height: 56)
            .background(Color.Background.default)
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(farmNameBorderColor(farmName.wrappedValue), lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var addressBorderColor: Color {
        hasAttemptedPrimary && location.selectedAddress == nil ? Color.Border.error : Color.Border.default
    }

    private func farmNameBorderColor(_ name: String) -> Color {
        hasAttemptedPrimary && name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? Color.Border.error : Color.Border.default
    }

    private var bottomCTA: some View {
        VStack(spacing: 0) {
            AppButton(ctaTitle, variant: .secondary, size: .medium, fullWidth: true) {
                hasAttemptedPrimary = true
                guard isValid else { return }
                onPrimary()
            }
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.top, Spacing.md)
            .padding(.bottom, Spacing.md)
            .background(Color.Background.default)
        }
    }

    // MARK: - Validation

    private var isValid: Bool {
        if showsFarmNameField, let farmName {
            return location.requiredInputError(farmName: farmName.wrappedValue) == nil && location.canProceed
        }
        return location.canProceed
    }

    private var validationMessage: String? {
        guard hasAttemptedPrimary else { return nil }
        if showsFarmNameField, let farmName {
            return location.requiredInputError(farmName: farmName.wrappedValue)
        }
        return location.selectedAddress == nil ? "주소지는 필수로 입력해주세요." : nil
    }
}

#if DEBUG
#Preview {
    FarmLocationPickerView(
        location: FarmLocationViewModel(),
        showsFarmNameField: true,
        farmName: .constant(""),
        onBack: {},
        onPrimary: {}
    )
}
#endif
