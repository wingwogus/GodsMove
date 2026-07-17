//
//  FarmLocationViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import CoreLocation
import Observation

@Observable
@MainActor
final class FarmLocationViewModel {
    enum LookupState: Equatable {
        case idle
        case resolvingCoordinate
        case loadingParcel
        case parcelNotFound
        case loaded
        case failed(String)
    }

    var isSearching = false
    var searchResults: [JusoAddress] = []
    var selectedAddress: JusoAddress?
    var resolvedCoordinate: GeoPoint?
    var selectedParcel: FarmlandParcel?
    var manualAreaText: String = ""
    var lookupState: LookupState = .idle

    /// 지적도 없는 밭을 지도에 직접 그리는 모드와 그 꼭짓점들.
    var isDrawingMode = false
    var drawnCoordinates: [GeoPoint] = []

    var manualAreaSqm: Double? {
        let trimmedText = manualAreaText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let areaSqm = Double(trimmedText), areaSqm > 0 else { return nil }
        return areaSqm
    }

    /// 사용자가 그린 폴리곤 면적(㎡). 3점 이상 & 면적>0일 때만 유효.
    var drawnAreaSqm: Double? {
        let area = FarmlandParcel.planarArea(of: drawnCoordinates)
        return area > 0 ? area : nil
    }

    var isDrawnPolygonValid: Bool {
        drawnCoordinates.count >= 3 && drawnAreaSqm != nil
    }

    var canProceed: Bool {
        guard selectedAddress != nil else { return false }
        return selectedParcel != nil || manualAreaSqm != nil || isDrawnPolygonValid
    }

    // MARK: - Submission fields
    //
    // 온보딩(draft)·MyPage(SaveFarmRequestDTO)가 각자 조립하지 않도록 제출 필드를 한 곳에 모은다.

    /// 대표 좌표: 작도 시 폴리곤 중심, 아니면 주소 지오코딩 좌표.
    var submissionCoordinate: GeoPoint? {
        if isDrawnPolygonValid {
            return FarmlandParcel.centroid(of: drawnCoordinates)
        }
        return resolvedCoordinate
    }

    /// 경계 좌표: 필지 있으면 필지, 없고 작도했으면 작도 좌표, 그 외 빈 배열.
    var submissionBoundaryCoordinates: [FarmBoundaryCoordinateDTO] {
        let source: [GeoPoint]
        if let parcel = selectedParcel {
            source = parcel.coordinates
        } else if isDrawnPolygonValid {
            source = drawnCoordinates
        } else {
            source = []
        }
        return source.map { FarmBoundaryCoordinateDTO(latitude: $0.latitude, longitude: $0.longitude) }
    }

    var submissionAreaSqm: Double? {
        selectedParcel?.areaSqm ?? drawnAreaSqm ?? manualAreaSqm
    }

    /// 필지(공식 지적)가 아니면 true — 수동 입력·작도 면적 모두 비공식.
    var submissionAreaIsManualEntry: Bool {
        selectedParcel == nil
    }

    var submissionPNU: String? {
        selectedParcel?.pnu
    }

    var submissionLandCategory: String? {
        selectedParcel?.jimokName
    }

    var submissionDataSource: FarmDataSourceDTO {
        if submissionPNU == nil && !submissionBoundaryCoordinates.isEmpty {
            return .onboardingUserDrawnPolygon
        }
        return .onboardingJusoVWorld
    }

    func requiredInputError(farmName: String) -> String? {
        let isAddressMissing = selectedAddress == nil
        let isFarmNameMissing = farmName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty

        switch (isAddressMissing, isFarmNameMissing) {
        case (true, true):
            return "주소지와 농지명은 필수로 입력해주세요."
        case (true, false):
            return "주소지는 필수로 입력해주세요."
        case (false, true):
            return "농지명은 필수로 입력해주세요."
        case (false, false):
            return nil
        }
    }

    private let addressSearch: any AddressSearching
    private let vworld: any FarmlandGeocoding & ParcelLookup
    private let landCharacteristics: any LandCharacteristicsLookup

    init(
        addressSearch: any AddressSearching = JusoAPIService(),
        vworld: any FarmlandGeocoding & ParcelLookup = VWorldAPIService(),
        landCharacteristics: any LandCharacteristicsLookup = LandCharacterAPIService()
    ) {
        self.addressSearch = addressSearch
        self.vworld = vworld
        self.landCharacteristics = landCharacteristics
    }

    func search(keyword: String) async {
        guard !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            searchResults = []
            return
        }
        isSearching = true
        defer { isSearching = false }
        do {
            searchResults = try await addressSearch.search(keyword: keyword)
        } catch is CancellationError {
            // A newer keystroke already superseded this search — leave state to the task that replaces it.
        } catch {
            searchResults = []
            lookupState = .failed("주소 검색 중 오류가 발생했어요")
        }
    }

    func selectAddress(_ address: JusoAddress) async {
        selectedAddress = address
        selectedParcel = nil
        manualAreaText = ""
        resolvedCoordinate = nil
        isDrawingMode = false
        drawnCoordinates = []
        await resolveCoordinate(for: address)
    }

    func handleMapTap(at coordinate: CLLocationCoordinate2D) async {
        if isDrawingMode {
            addDrawnVertex(coordinate)
            return
        }
        await fetchParcel(at: coordinate)
    }

    // MARK: - Polygon drawing

    /// 지도 직접 그리기 시작. 기존 수동 면적 입력은 배타적이므로 비운다.
    func beginDrawing() {
        isDrawingMode = true
        drawnCoordinates = []
        manualAreaText = ""
    }

    func addDrawnVertex(_ coordinate: CLLocationCoordinate2D) {
        drawnCoordinates.append(GeoPoint(coordinate))
    }

    func undoLastDrawnVertex() {
        guard !drawnCoordinates.isEmpty else { return }
        drawnCoordinates.removeLast()
    }

    func clearDrawing() {
        drawnCoordinates = []
    }

    /// 작도를 취소하고 폴백 선택(면적 입력/직접 그리기)으로 돌아간다.
    func cancelDrawing() {
        isDrawingMode = false
        drawnCoordinates = []
    }

    /// 3점 이상이면 작도를 확정한다. 반환값은 성공 여부.
    @discardableResult
    func finishDrawing() -> Bool {
        guard isDrawnPolygonValid else { return false }
        isDrawingMode = false
        return true
    }

    private func resolveCoordinate(for address: JusoAddress) async {
        lookupState = .resolvingCoordinate
        do {
            let coordinate = try await vworld.geocode(roadAddress: address.roadAddrPart1)
            resolvedCoordinate = GeoPoint(coordinate)
            await fetchParcel(at: coordinate)
        } catch {
            lookupState = .failed("주소의 좌표를 확인하지 못했어요")
        }
    }

    private func fetchParcel(at coordinate: CLLocationCoordinate2D) async {
        lookupState = .loadingParcel
        do {
            let parcel = try await vworld.fetchParcel(at: coordinate)
            selectedParcel = parcel
            drawnCoordinates = []
            isDrawingMode = false
            lookupState = .loaded
            await fetchOfficialArea(pnu: parcel.pnu)
        } catch FarmLocationAPIError.noParcelFound {
            selectedParcel = nil
            lookupState = .parcelNotFound
        } catch {
            selectedParcel = nil
            lookupState = .failed("지적도 정보를 불러오지 못했어요")
        }
    }

    private func fetchOfficialArea(pnu: String) async {
        guard let info = try? await landCharacteristics.fetchLandCharacteristics(pnu: pnu) else { return }
        guard var parcel = selectedParcel, parcel.pnu == pnu else { return }
        parcel.areaSqm = info.officialAreaSqm
        parcel.jimok = info.jimokName
        selectedParcel = parcel
    }
}
