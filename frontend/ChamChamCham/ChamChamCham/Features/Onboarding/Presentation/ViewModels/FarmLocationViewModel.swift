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
        /// 주소→좌표 변환이 도로명·지번 모두 실패한 상태. 막다른 에러가 아니라, 지도에 직접
        /// 표시하도록 안내하는 폴백 상태다. `retryable`은 네트워크성 실패라 재시도가 의미 있는 경우.
        case coordinateUnavailable(retryable: Bool)
        case failed(String)
    }

    var isSearching = false
    var searchResults: [JusoAddress] = []
    var selectedAddress: JusoAddress?
    /// `selectedAddress`가 역지오코딩이 아니라 `setManualAddress`로 채워졌는지. 작도 완료
    /// 후 주소 카드의 안내 문구를 "자동으로 확인된 위치" 대신 "직접 입력한 주소"로 갈리는 데 쓴다.
    var isManualAddress = false
    /// 작도 완료 후 역지오코딩이 실패해 주소를 못 채운 상태. true면 상단 주소 필드(검색 시트를
    /// 여는 버튼)가 직접 타이핑 가능한 텍스트 필드로 전환된다(`FarmLocationView`/
    /// `FarmLocationPickerView`의 `addressOverlayField`). 한 번 켜지면 타이핑 중에 값이
    /// 바뀌어도 꺼지지 않는다(sticky) — 안 그러면 입력 도중 필드가 버튼으로 되돌아간다.
    /// `beginDrawing`/`cancelDrawing`/`selectAddress`로 새 맥락이 시작되거나 `retryDrawnAddress`가
    /// 성공해야 꺼진다.
    var needsManualAddressEntry = false
    var resolvedCoordinate: GeoPoint?
    var selectedParcel: FarmlandParcel?
    var manualAreaText: String = ""
    var lookupState: LookupState = .idle

    /// 지적도 없는 밭을 지도에 직접 그리는 모드와 그 꼭짓점들.
    var isDrawingMode = false
    var drawnCoordinates: [GeoPoint] = []

    /// 상시 작도 진입 버튼으로 기존 필지를 대체하며 그리기 시작했을 때, 취소 시 되돌릴
    /// 필지 스냅샷. 필지가 없는 상태에서 시작했다면 nil.
    private var parcelBeforeDrawing: FarmlandParcel?

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
        guard let selectedAddress, hasContent(selectedAddress) else { return false }
        return selectedParcel != nil || manualAreaSqm != nil || isDrawnPolygonValid
    }

    /// `selectAddress(_:)`로 기존 밭 데이터를 그대로 채우는 수정 진입 경로는 `makeAddress`의
    /// 둘 다 빈 값 가드를 거치지 않는다. 레거시 데이터로 도로명·지번이 모두 비어 있는 밭이면
    /// `selectedAddress`가 non-nil이어도 내용이 없는 것이므로, "주소 없음"과 동일하게 취급해
    /// 저장 버튼이 기존 검증 문구("주소지는 필수로 입력해주세요")로 막히게 한다.
    private func hasContent(_ address: JusoAddress) -> Bool {
        !address.roadAddrPart1.trimmingCharacters(in: .whitespaces).isEmpty
            || !address.jibunAddr.trimmingCharacters(in: .whitespaces).isEmpty
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
        let isAddressMissing = selectedAddress.map { !hasContent($0) } ?? true
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
    private let vworld: any FarmlandGeocoding & ParcelLookup & ReverseGeocoding
    private let landCharacteristics: any LandCharacteristicsLookup

    init(
        addressSearch: any AddressSearching = JusoAPIService(),
        vworld: any FarmlandGeocoding & ParcelLookup & ReverseGeocoding = VWorldAPIService(),
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
            // 실패를 "오류"로 표현하지 않는다 — 국내 주소 검색 서비스가 일부 네트워크
            // 경로에서 응답하지 않는 건 이 앱 특성상 정상적으로 있을 수 있는 경로다.
            // 항상 다음 행동(지도에 직접 그리기)을 제시한다.
            lookupState = .failed("주소를 찾지 못했어요. 지도에서 직접 위치를 표시해주세요.")
        }
    }

    func selectAddress(_ address: JusoAddress) async {
        selectedAddress = address
        selectedParcel = nil
        manualAreaText = ""
        resolvedCoordinate = nil
        isDrawingMode = false
        drawnCoordinates = []
        needsManualAddressEntry = false
        await resolveCoordinate(for: address)
    }

    func handleMapTap(at coordinate: CLLocationCoordinate2D) async {
        if isDrawingMode {
            addDrawnVertex(coordinate)
            return
        }
        // 새 탭은 활성 좌표를 바꾸므로 주소를 탭 위치 기준으로 다시 채운다.
        await fetchParcel(at: coordinate, refreshAddress: true)
    }

    // MARK: - Polygon drawing

    /// 지도 직접 그리기 시작. 기존 수동 면적 입력은 배타적이므로 비운다. 기존 필지가
    /// 있었다면 스냅샷해두고 비운다 — 작도 중엔 필지 폴리곤과 겹쳐 보이지 않도록 하고,
    /// 취소 시 `cancelDrawing()`에서 되돌린다.
    func beginDrawing() {
        parcelBeforeDrawing = selectedParcel
        selectedParcel = nil
        isDrawingMode = true
        drawnCoordinates = []
        manualAreaText = ""
        needsManualAddressEntry = false
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

    /// 작도를 취소하고 폴백 선택(면적 입력/직접 그리기)으로 돌아간다. 상시 진입 버튼으로
    /// 기존 필지를 대체하던 중이었다면 그 필지를 복원한다.
    func cancelDrawing() {
        isDrawingMode = false
        drawnCoordinates = []
        selectedParcel = parcelBeforeDrawing
        parcelBeforeDrawing = nil
        needsManualAddressEntry = false
    }

    /// 3점 이상이면 작도를 확정한다. 반환값은 성공 여부.
    ///
    /// 지적도에 없는 밭이므로 폴리곤 중심 좌표를 역지오코딩해 주소를 채운다. 이전 검색 주소가
    /// 그대로 남아 엉뚱한 위치와 짝지어지는 것을 막기 위해, 확정 즉시 주소를 무효화한 뒤 다시 채운다.
    @discardableResult
    func finishDrawing() async -> Bool {
        guard isDrawnPolygonValid else { return false }
        isDrawingMode = false
        parcelBeforeDrawing = nil
        selectedAddress = nil
        isManualAddress = false
        guard let centroid = FarmlandParcel.centroid(of: drawnCoordinates) else { return true }
        if let reverse = try? await vworld.reverseGeocode(at: centroid.clLocationCoordinate) {
            selectedAddress = makeAddress(road: reverse.roadAddress, jibun: reverse.jibunAddress)
        }
        // 역지오코딩이 실패했으면(해외 네트워크 등) 상단 주소 필드를 직접 입력으로 전환한다.
        needsManualAddressEntry = (selectedAddress == nil)
        return true
    }

    /// 작도 완료 직후 역지오코딩이 실패해 주소가 비어 있을 때 다시 시도한다. VWorld는
    /// 거리·신뢰도 정보를 주지 않으므로 성공해도 주소가 실제 위치와 다를 수 있다 —
    /// 화면에서 사용자가 육안으로 확인하도록 안내한다.
    func retryDrawnAddress() async {
        guard let centroid = FarmlandParcel.centroid(of: drawnCoordinates) else { return }
        if let reverse = try? await vworld.reverseGeocode(at: centroid.clLocationCoordinate) {
            selectedAddress = makeAddress(road: reverse.roadAddress, jibun: reverse.jibunAddress)
            isManualAddress = false
            needsManualAddressEntry = false
        }
    }

    /// 역지오코딩이 실패했을 때(네트워크 미도달 등) 사용자가 직접 타이핑한 주소로 진행할 수
    /// 있게 한다. 폴리곤 좌표가 실제 위치의 진실이고, 주소는 사람이 알아보기 위한 라벨이므로
    /// 자동 조회가 안 되면 사용자가 직접 채우는 것으로 대체 가능하다. 상단 주소 필드의 실시간
    /// 텍스트 바인딩이라 매 타이핑마다 호출된다 — 빈 문자열은 `selectedAddress`를 nil로 되돌려
    /// 지우기가 그대로 반영되게 한다. `needsManualAddressEntry`는 건드리지 않는다(sticky):
    /// 타이핑 중에 필드가 다시 버튼으로 바뀌면 안 되기 때문이다.
    func setManualAddress(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            selectedAddress = nil
            isManualAddress = false
            return
        }
        selectedAddress = JusoAddress(roadAddrPart1: "", jibunAddr: text, bdNm: "")
        isManualAddress = true
    }

    /// 도로명/지번 중 존재하는 값으로 주소를 만든다. 둘 다 비면 nil(주소 미확정).
    /// 도로명이 없는 농지는 지번만 채워진 주소로 표시·저장된다.
    private func makeAddress(road: String?, jibun: String?) -> JusoAddress? {
        let roadText = road?.trimmingCharacters(in: .whitespaces) ?? ""
        let jibunText = jibun?.trimmingCharacters(in: .whitespaces) ?? ""
        guard !roadText.isEmpty || !jibunText.isEmpty else { return nil }
        return JusoAddress(roadAddrPart1: roadText, jibunAddr: jibunText, bdNm: "")
    }

    /// 좌표변환에 실패한 주소로 재시도한다(네트워크성 실패 후 "다시 시도" 용). 선택된 주소가
    /// 없으면 아무것도 하지 않는다.
    func retryCoordinate() async {
        guard let address = selectedAddress else { return }
        await resolveCoordinate(for: address)
    }

    private func resolveCoordinate(for address: JusoAddress) async {
        lookupState = .resolvingCoordinate
        do {
            let coordinate = try await vworld.geocode(
                roadAddress: address.roadAddrPart1,
                jibunAddress: address.jibunAddr
            )
            resolvedCoordinate = GeoPoint(coordinate)
            // 검색으로 선택한 주소를 유지해야 하므로 주소는 다시 채우지 않는다.
            await fetchParcel(at: coordinate, refreshAddress: false)
        } catch {
            // 도로명·지번 모두 실패. 막다른 에러 대신 "지도에 직접 표시" 안내 폴백으로 전환한다.
            // 선택 주소는 유지해 사용자가 어떤 주소를 다루던 중인지 잃지 않는다.
            resolvedCoordinate = nil
            lookupState = .coordinateUnavailable(retryable: Self.isRetryable(error))
        }
    }

    /// 네트워크/디코딩 실패는 재시도가 의미 있고, 주소 자체가 좌표를 못 가진 경우(`noResult`)는
    /// 재시도해도 같으므로 바로 "지도에 직접 표시"로 유도한다.
    private static func isRetryable(_ error: Error) -> Bool {
        switch error {
        case FarmLocationAPIError.network, FarmLocationAPIError.decoding:
            return true
        default:
            return false
        }
    }

    /// - Parameter refreshAddress: 지도 탭처럼 활성 좌표가 바뀌는 경우 true. 이전 선택 상태를
    ///   무효화하고 역지오코딩으로 주소를 다시 채운다. JUSO 검색 흐름은 false(주소 유지).
    private func fetchParcel(at coordinate: CLLocationCoordinate2D, refreshAddress: Bool) async {
        lookupState = .loadingParcel

        if refreshAddress {
            // 활성 좌표가 바뀌는 즉시 이전 선택을 무효화한다. 주소·수동면적·작도 폴리곤이
            // 이전 위치 값으로 남아 새 위치와 섞이는 것을 방지(주소/좌표 불일치 근본 수정).
            selectedAddress = nil
            manualAreaText = ""
            drawnCoordinates = []
            isDrawingMode = false
            resolvedCoordinate = GeoPoint(coordinate)
        }

        // refreshAddress일 때만 역지오코딩을 병행(실패는 nil로 관용, 도로명 채우기 용도).
        async let reverseTask: ReverseGeocodedAddress? = refreshAddress
            ? (try? await vworld.reverseGeocode(at: coordinate))
            : nil

        do {
            let parcel = try await vworld.fetchParcel(at: coordinate)
            selectedParcel = parcel
            drawnCoordinates = []
            isDrawingMode = false
            lookupState = .loaded
            if refreshAddress {
                // 지번은 GetFeature 값이 정확하므로 우선 사용하고, 도로명만 역지오코딩으로 채운다.
                selectedAddress = makeAddress(road: (await reverseTask)?.roadAddress, jibun: parcel.jibunAddr)
            }
            await fetchOfficialArea(pnu: parcel.pnu)
        } catch FarmLocationAPIError.noParcelFound {
            selectedParcel = nil
            lookupState = .parcelNotFound
            if refreshAddress {
                let reverse = await reverseTask
                selectedAddress = makeAddress(road: reverse?.roadAddress, jibun: reverse?.jibunAddress)
            }
        } catch {
            selectedParcel = nil
            if refreshAddress {
                let reverse = await reverseTask
                selectedAddress = makeAddress(road: reverse?.roadAddress, jibun: reverse?.jibunAddress)
            }
            // "오류"로 표현하지 않는다 — 지적도 조회가 일부 네트워크 경로에서 응답하지
            // 않는 건 이 앱 특성상 정상적으로 있을 수 있는 경로다. 항상 다음 행동(지도에
            // 직접 그리기)을 제시한다.
            lookupState = .failed("지적도 정보를 불러오지 못했어요. 지도에서 직접 경계를 그려주세요.")
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
