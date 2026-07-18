//
//  FarmLocationMapSection.swift
//  ChamChamCham
//
//  Created by iyungui on 7/17/26.
//

import CoreLocation
import MapKit
import SwiftUI

/// 재배지 지도 상호작용을 담는 공유 컴포넌트. 온보딩(`FarmLocationView`)과
/// 마이페이지(`FarmAddView`)가 함께 사용한다.
///
/// 담는 것: 지도(표준/위성), 줌·위성 토글, 현재 위치, 탭→필지 조회, 필지 폴리곤,
/// 지적도 없는 밭의 직접 작도(폴리곤), 그리고 하단 상태/필지/작도 카드.
/// 담지 않는 것: 주소·농지명 입력 필드(화면마다 배치가 달라 각 화면이 오버레이/폼으로 배치).
struct FarmLocationMapSection: View {
    @Bindable var viewModel: FarmLocationViewModel
    /// 우상단 컨트롤(줌·위성)의 상단 여백. 온보딩은 상단 오버레이 필드를 피하려고 크게 준다.
    var controlsTopInset: CGFloat = 20
    /// 하단 카드가 화면 하단 CTA를 피하도록 두는 여백.
    var bottomInset: CGFloat = 32

    @State private var locationManager = LocationManager()
    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var cameraSpanMeters: CLLocationDistance = 300
    @State private var mapCenter = FarmLocationMapSection.seoul
    @State private var mapStyleIsSatellite = false
    @State private var didSetInitialCamera = false
    @State private var didCenterOnCurrentLocation = false
    @State private var manualAreaChosen = false

    /// 현재 위치 승인을 못 받았을 때의 폴백 카메라(서울 시청).
    private static let seoul = CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780)

    var body: some View {
        ZStack(alignment: .top) {
            mapSection

            controls
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .padding(.trailing, 20)
                .padding(.top, controlsTopInset)

            VStack {
                Spacer()
                bottomCard
            }
            .padding(.horizontal, 20)
            .padding(.bottom, bottomInset)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.Object.muted)
        .onAppear(perform: setInitialCameraIfNeeded)
        .task { locationManager.requestAuthorization() }
        .onChange(of: locationManager.lastCoordinate) { _, newValue in
            centerOnCurrentLocationIfNeeded(newValue)
        }
        .onChange(of: viewModel.resolvedCoordinate) { _, newValue in
            guard let newValue else { return }
            setCamera(to: newValue.clLocationCoordinate, span: cameraSpanMeters)
        }
        .onChange(of: viewModel.lookupState) { _, _ in
            manualAreaChosen = false
        }
    }

    // MARK: - Map

    private var mapSection: some View {
        MapReader { proxy in
            Map(position: $cameraPosition) {
                if locationManager.isAuthorized {
                    UserAnnotation()
                }

                if let coordinate = viewModel.resolvedCoordinate,
                   !viewModel.isDrawingMode, viewModel.drawnCoordinates.isEmpty {
                    Marker("농지", coordinate: coordinate.clLocationCoordinate)
                }

                if let parcel = viewModel.selectedParcel {
                    MapPolygon(coordinates: parcel.coordinates.map(\.clLocationCoordinate))
                        .foregroundStyle(Color.Object.primary.opacity(0.25))
                        .stroke(Color.Object.primary, lineWidth: 2)
                }

                drawnShape

                if viewModel.isDrawingMode {
                    ForEach(Array(viewModel.drawnCoordinates.enumerated()), id: \.offset) { _, point in
                        Annotation("", coordinate: point.clLocationCoordinate) {
                            Circle()
                                .fill(Color.Object.primary)
                                .frame(width: 12, height: 12)
                                .overlay(Circle().stroke(.white, lineWidth: 2))
                        }
                    }
                }
            }
            .mapStyle(mapStyleIsSatellite ? .hybrid(elevation: .realistic) : .standard)
            .onMapCameraChange { context in
                mapCenter = context.region.center
            }
            .onTapGesture { screenPoint in
                guard let coordinate = proxy.convert(screenPoint, from: .local) else { return }
                Task { await viewModel.handleMapTap(at: coordinate) }
            }
        }
    }

    @MapContentBuilder
    private var drawnShape: some MapContent {
        if viewModel.drawnCoordinates.count >= 3 {
            MapPolygon(coordinates: viewModel.drawnCoordinates.map(\.clLocationCoordinate))
                .foregroundStyle(Color.Object.primary.opacity(0.2))
                .stroke(Color.Object.primary, lineWidth: 2)
        } else if viewModel.drawnCoordinates.count == 2 {
            MapPolyline(coordinates: viewModel.drawnCoordinates.map(\.clLocationCoordinate))
                .stroke(Color.Object.primary, lineWidth: 2)
        }
    }

    // MARK: - Controls (satellite + zoom)

    private var controls: some View {
        VStack(spacing: 12) {
            controlSquare(systemName: mapStyleIsSatellite ? "map" : "globe.asia.australia.fill") {
                mapStyleIsSatellite.toggle()
            }
            zoomControls
        }
    }

    private func controlSquare(systemName: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.Icon.default)
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .background(Color.Background.default)
        .overlay {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.Border.default, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
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

    // MARK: - Bottom card

    @ViewBuilder
    private var bottomCard: some View {
        if viewModel.isDrawingMode {
            drawingControlsCard
        } else if viewModel.isDrawnPolygonValid {
            drawnSummaryCard
        } else {
            switch viewModel.lookupState {
            case .resolvingCoordinate, .loadingParcel:
                loadingCard
            case .loaded:
                if let parcel = viewModel.selectedParcel {
                    parcelCard(parcel)
                }
            case .parcelNotFound:
                notFoundCard
            case .failed(let message):
                mapStatusCard { errorBanner(message) }
            case .idle:
                EmptyView()
            }
        }
    }

    private var loadingCard: some View {
        mapStatusCard {
            HStack(spacing: 12) {
                ProgressView()
                Text("재배지 정보를 확인하고 있어요.")
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.subtle)
            }
        }
    }

    private func parcelCard(_ parcel: FarmlandParcel) -> some View {
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

    @ViewBuilder
    private var notFoundCard: some View {
        mapStatusCard {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                if manualAreaChosen {
                    errorBanner("지도에서 필지를 찾지 못했어요. 면적을 직접 입력해주세요.")
                    AppTextField(
                        label: "면적 (㎡)",
                        placeholder: "숫자만 입력하세요",
                        text: Binding(
                            get: { viewModel.manualAreaText },
                            set: { viewModel.manualAreaText = $0.filter { $0.isNumber || $0 == "." } }
                        ),
                        keyboardType: .decimalPad
                    )
                } else {
                    errorBanner("지도에서 필지를 찾지 못했어요. 면적을 직접 입력하거나 지도에 직접 그려주세요.")
                    HStack(spacing: Spacing.sm) {
                        AppButton("면적 직접 입력", variant: .neutral, size: .small, fullWidth: true) {
                            manualAreaChosen = true
                        }
                        AppButton("지도에 직접 그리기", icon: .asset("edit"), variant: .secondary, size: .small, fullWidth: true) {
                            viewModel.beginDrawing()
                        }
                    }
                }
            }
        }
    }

    private var drawingControlsCard: some View {
        mapStatusCard {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                if let areaSqm = viewModel.drawnAreaSqm {
                    Text(FarmlandParcel.formattedArea(sqm: areaSqm))
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.default)
                } else {
                    Text("밭의 꼭짓점을 지도에 3개 이상 찍어주세요.")
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.subtle)
                }

                HStack(spacing: Spacing.sm) {
                    AppButton("되돌리기", icon: .system("arrow.uturn.backward"), variant: .neutral, size: .small, fullWidth: true,
                              appearsDisabled: viewModel.drawnCoordinates.isEmpty) {
                        viewModel.undoLastDrawnVertex()
                    }
                    AppButton("초기화", icon: .system("trash"), variant: .neutral, size: .small, fullWidth: true,
                              appearsDisabled: viewModel.drawnCoordinates.isEmpty) {
                        viewModel.clearDrawing()
                    }
                }

                HStack(spacing: Spacing.sm) {
                    AppButton("취소", variant: .neutral, size: .small, fullWidth: true) {
                        viewModel.cancelDrawing()
                    }
                    AppButton("완료", icon: .asset("check"), variant: .secondary, size: .small, fullWidth: true,
                              appearsDisabled: !viewModel.isDrawnPolygonValid) {
                        Task { await viewModel.finishDrawing() }
                    }
                }
            }
        }
    }

    private var drawnSummaryCard: some View {
        mapStatusCard {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("직접 그린 재배지")
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.subtle)
                if let areaSqm = viewModel.drawnAreaSqm {
                    Text(FarmlandParcel.formattedArea(sqm: areaSqm))
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.default)
                }
                AppButton("다시 그리기", icon: .asset("edit"), variant: .neutral, size: .small, fullWidth: true) {
                    viewModel.beginDrawing()
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Card helpers

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
            AppIconView(source: .asset("info"), size: 20)
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

    // MARK: - Camera

    private func setInitialCameraIfNeeded() {
        guard !didSetInitialCamera else { return }
        didSetInitialCamera = true
        if let coordinate = viewModel.resolvedCoordinate {
            setCamera(to: coordinate.clLocationCoordinate, span: cameraSpanMeters)
        } else {
            setCamera(to: Self.seoul, span: 3000)
        }
    }

    private func centerOnCurrentLocationIfNeeded(_ coordinate: GeoPoint?) {
        guard let coordinate,
              viewModel.resolvedCoordinate == nil,
              !didCenterOnCurrentLocation else { return }
        didCenterOnCurrentLocation = true
        setCamera(to: coordinate.clLocationCoordinate, span: 800)
    }

    private func setCamera(to coordinate: CLLocationCoordinate2D, span: CLLocationDistance) {
        cameraSpanMeters = span
        mapCenter = coordinate
        cameraPosition = .region(
            MKCoordinateRegion(center: coordinate, latitudinalMeters: span, longitudinalMeters: span)
        )
    }

    private func zoom(by factor: Double) {
        cameraSpanMeters = min(max(cameraSpanMeters * factor, 80), 5000)
        cameraPosition = .region(
            MKCoordinateRegion(center: mapCenter, latitudinalMeters: cameraSpanMeters, longitudinalMeters: cameraSpanMeters)
        )
    }
}
