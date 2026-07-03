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

    private var isValid: Bool {
        !viewModel.draft.farmName.trimmingCharacters(in: .whitespaces).isEmpty
            && farmLocationViewModel.canProceed
    }

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
                Text("농지 위치")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextSecondary)
                Text("농장 이름과 위치를\n입력해주세요")
                    .font(.appTitle)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    AppTextField(label: "*농장 이름", placeholder: "예) 이랑농장", text: $viewModel.draft.farmName)

                    addressSection

                    if farmLocationViewModel.resolvedCoordinate != nil {
                        mapSection
                        parcelInfoSection
                    }
                }
                .padding(.vertical, Spacing.sm)
            }

            PrimaryButton(title: "다음") {
                applySelectionToDraft()
                viewModel.goNext()
            }
            .disabled(!isValid)
            .opacity(isValid ? 1 : 0.5)
        }
        .padding(Spacing.lg)
        .sheet(isPresented: $isSearchSheetPresented) {
            AddressSearchSheet(viewModel: farmLocationViewModel) { address in
                Task { await farmLocationViewModel.selectAddress(address) }
            }
        }
        .onChange(of: farmLocationViewModel.resolvedCoordinate) { _, newValue in
            guard let newValue else { return }
            cameraPosition = .region(
                MKCoordinateRegion(center: newValue.clLocationCoordinate, latitudinalMeters: 300, longitudinalMeters: 300)
            )
        }
    }

    private var addressSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("*농지 주소")
                .font(.appCaption)
                .foregroundStyle(Color.appTextSecondary)

            Button {
                isSearchSheetPresented = true
            } label: {
                HStack {
                    Image(systemName: "magnifyingglass")
                    Text(farmLocationViewModel.selectedAddress?.roadAddrPart1 ?? "주소 검색")
                        .lineLimit(1)
                    Spacer()
                }
                .foregroundStyle(
                    farmLocationViewModel.selectedAddress == nil ? Color.appTextSecondary : Color.appTextPrimary
                )
                .padding(Spacing.md)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }

            if case .failed(let message) = farmLocationViewModel.lookupState {
                errorBanner(message)
            }
        }
    }

    private var mapSection: some View {
        MapReader { proxy in
            Map(position: $cameraPosition) {
                if let coordinate = farmLocationViewModel.resolvedCoordinate {
                    Marker("농지", coordinate: coordinate.clLocationCoordinate)
                }
                if let parcel = farmLocationViewModel.selectedParcel {
                    MapPolygon(coordinates: parcel.coordinates.map(\.clLocationCoordinate))
                        .foregroundStyle(Color.appPrimary.opacity(0.25))
                        .stroke(Color.appPrimary, lineWidth: 2)
                }
            }
            .onTapGesture { screenPoint in
                guard let coordinate = proxy.convert(screenPoint, from: .local) else { return }
                Task { await farmLocationViewModel.handleMapTap(at: coordinate) }
            }
        }
        .frame(height: 220)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var parcelInfoSection: some View {
        switch farmLocationViewModel.lookupState {
        case .resolvingCoordinate, .loadingParcel:
            LoadingView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
        case .loaded:
            if let parcel = farmLocationViewModel.selectedParcel {
                CardView {
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(parcel.jibunAddr)
                            .font(.appBody)
                        HStack(spacing: Spacing.sm) {
                            Text("지목 \(parcel.jimokName)")
                            Text("·")
                            Text(parcel.formattedArea)
                        }
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextSecondary)
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

    private func errorBanner(_ message: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(Color.appPrimary)
            Text(message)
                .font(.appCaption)
                .foregroundStyle(Color.appTextPrimary)
        }
        .padding(Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appPrimary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
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
        } else if let manualArea = Double(farmLocationViewModel.manualAreaText) {
            viewModel.draft.farmPNU = nil
            viewModel.draft.farmLandCategory = nil
            viewModel.draft.farmAreaSqm = manualArea
            viewModel.draft.farmAreaIsManualEntry = true
        }
    }
}

#Preview {
    FarmLocationView()
        .environment(OnboardingViewModel())
}
