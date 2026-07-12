//
//  FarmLocationView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import MapKit
import SwiftUI

struct FarmLocationView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @State private var farmLocationViewModel = FarmLocationViewModel()
    @State private var isSearchSheetPresented = false
    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var cameraSpanMeters: CLLocationDistance = 300
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
        .overlay(alignment: .bottom) {
            bottomCTA
        }
        .sheet(isPresented: $isSearchSheetPresented) {
            AddressSearchSheet(viewModel: farmLocationViewModel) { address in
                Task { await farmLocationViewModel.selectAddress(address) }
            }
        }
        .onChange(of: farmLocationViewModel.resolvedCoordinate) { _, newValue in
            guard let newValue else { return }
            cameraPosition = .region(
                MKCoordinateRegion(center: newValue.clLocationCoordinate, latitudinalMeters: cameraSpanMeters, longitudinalMeters: cameraSpanMeters)
            )
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

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("대표 재배지 설정하기")
                .appTypography(.headlineMedium)
                .foregroundStyle(Color.Text.default)

            Text("재배지의 주소명과 농지명을 입력해주세요.")
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var mapOverlaySection: some View {
        ZStack(alignment: .top) {
            mapSection

            VStack(spacing: 12) {
                addressOverlayField
                farmNameOverlayField

                if let requiredInputError {
                    Text(requiredInputError)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if case .failed(let message) = farmLocationViewModel.lookupState {
                    errorBanner(message)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)

            zoomControls
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                .padding(.trailing, 20)
                .padding(.top, 190)

            VStack {
                Spacer()
                parcelInfoSection
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 116)
        }
        .frame(maxWidth: .infinity)
        .frame(maxHeight: .infinity)
        .background(Color.Object.muted)
    }

    private var addressOverlayField: some View {
        Button {
            isSearchSheetPresented = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(Color.Icon.default)
                Text(farmLocationViewModel.selectedAddress?.roadAddrPart1 ?? "주소지를 입력해주세요.")
                    .appTypography(.bodyLarge)
                    .foregroundStyle(farmLocationViewModel.selectedAddress == nil ? Color.Text.muted : Color.Text.default)
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

    private var mapSection: some View {
        MapReader { proxy in
            Map(position: $cameraPosition) {
                if let coordinate = farmLocationViewModel.resolvedCoordinate {
                    Marker("농지", coordinate: coordinate.clLocationCoordinate)
                }
                if let parcel = farmLocationViewModel.selectedParcel {
                    MapPolygon(coordinates: parcel.coordinates.map(\.clLocationCoordinate))
                        .foregroundStyle(Color.Object.primary.opacity(0.25))
                        .stroke(Color.Object.primary, lineWidth: 2)
                }
            }
            .overlay {
                if farmLocationViewModel.resolvedCoordinate == nil {
                    mapPlaceholder
                }
            }
            .onTapGesture { screenPoint in
                guard let coordinate = proxy.convert(screenPoint, from: .local) else { return }
                Task { await farmLocationViewModel.handleMapTap(at: coordinate) }
            }
        }
    }

    private var mapPlaceholder: some View {
        ZStack {
            Color.Object.muted
            Image(systemName: "map")
                .font(.system(size: 42, weight: .medium))
                .foregroundStyle(Color.Icon.disabled)
        }
    }

    private var zoomControls: some View {
        VStack(spacing: 0) {
            zoomButton(systemName: "plus") { zoom(by: 0.5) }
            Divider()
                .background(Color.Border.default)
                .padding(.horizontal, 8)
            zoomButton(systemName: "minus") { zoom(by: 2.0) }
        }
        .frame(width: 48, height: 104)
        .background(Color.Background.default)
        .overlay {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.Border.default, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func zoomButton(systemName: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.Icon.default)
                .frame(width: 48, height: 51)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var parcelInfoSection: some View {
        switch farmLocationViewModel.lookupState {
        case .resolvingCoordinate, .loadingParcel:
            mapStatusCard {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("재배지 정보를 확인하고 있어요.")
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.subtle)
                }
            }
        case .loaded:
            if let parcel = farmLocationViewModel.selectedParcel {
                mapStatusCard {
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(parcel.jibunAddr)
                            .appTypography(.bodyMedium)
                        HStack(spacing: Spacing.sm) {
                            Text("지목 \(parcel.jimokName)")
                            Text("·")
                            Text(parcel.formattedArea)
                        }
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.subtle)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        case .parcelNotFound:
            manualAreaFallback
        case .idle, .failed:
            EmptyView()
        }
    }

    private var manualAreaFallback: some View {
        mapStatusCard {
            VStack(alignment: .leading, spacing: Spacing.sm) {
            errorBanner("지도에서 필지를 찾지 못했어요. 면적을 직접 입력해주세요.")
            AppTextField(
                label: "면적 (㎡)",
                placeholder: "숫자만 입력하세요",
                text: Binding(
                    get: { farmLocationViewModel.manualAreaText },
                    set: { farmLocationViewModel.manualAreaText = $0.filter { $0.isNumber || $0 == "." } }
                ),
                keyboardType: .decimalPad
            )
            }
        }
    }

    private func mapStatusCard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Background.default.opacity(0.96))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 12, y: 4)
    }

    private func errorBanner(_ message: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(Color.Icon.primary)
            Text(message)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.default)
        }
        .padding(Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.primarySubtle)
        .clipShape(RoundedRectangle(cornerRadius: 8))
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

    private func zoom(by factor: Double) {
        guard let coordinate = farmLocationViewModel.resolvedCoordinate else { return }
        cameraSpanMeters = min(max(cameraSpanMeters * factor, 80), 2000)
        cameraPosition = .region(
            MKCoordinateRegion(
                center: coordinate.clLocationCoordinate,
                latitudinalMeters: cameraSpanMeters,
                longitudinalMeters: cameraSpanMeters
            )
        )
    }

    private func applySelectionToDraft() {
        guard let address = farmLocationViewModel.selectedAddress else { return }
        viewModel.draft.farmRoadAddress = address.roadAddrPart1
        viewModel.draft.farmJibunAddress = address.jibunAddr
        viewModel.draft.farmLatitude = farmLocationViewModel.resolvedCoordinate?.latitude
        viewModel.draft.farmLongitude = farmLocationViewModel.resolvedCoordinate?.longitude

        if let parcel = farmLocationViewModel.selectedParcel {
            viewModel.draft.farmPNU = parcel.pnu
            viewModel.draft.farmLandCategory = parcel.jimokName
            viewModel.draft.farmAreaSqm = parcel.areaSqm
            viewModel.draft.farmAreaIsManualEntry = false
        } else if let manualArea = farmLocationViewModel.manualAreaSqm {
            viewModel.draft.farmPNU = nil
            viewModel.draft.farmLandCategory = nil
            viewModel.draft.farmAreaSqm = manualArea
            viewModel.draft.farmAreaIsManualEntry = true
        }
    }
}

#if DEBUG
#Preview {
    FarmLocationView()
        .environment(OnboardingViewModel.preview())
}
#endif
