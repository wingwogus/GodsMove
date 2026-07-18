//
//  FarmLocationDrawingTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/17/26.
//

import CoreLocation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("FarmLocationViewModel polygon drawing")
struct FarmLocationDrawingTests {

    private func makeViewModel(parcel: FarmlandParcel? = nil) -> FarmLocationViewModel {
        FarmLocationViewModel(
            addressSearch: StubAddressSearch(),
            vworld: StubVWorld(parcel: parcel),
            landCharacteristics: StubLandCharacteristics()
        )
    }

    @Test("작도 모드에서 지도 탭은 꼭짓점만 추가하고 필지 조회를 하지 않는다")
    func tapInDrawingModeAddsVertex() async {
        let viewModel = makeViewModel(parcel: nil)
        viewModel.beginDrawing()

        await viewModel.handleMapTap(at: CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0))

        #expect(viewModel.drawnCoordinates.count == 1)
        #expect(viewModel.selectedParcel == nil)
        #expect(viewModel.lookupState == .idle) // fetchParcel이 호출되지 않아 상태 변화 없음
    }

    @Test("3점 미만 폴리곤은 유효하지 않다")
    func fewerThanThreeVerticesInvalid() {
        let viewModel = makeViewModel()
        viewModel.beginDrawing()
        viewModel.addDrawnVertex(CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0))
        viewModel.addDrawnVertex(CLLocationCoordinate2D(latitude: 37.5, longitude: 127.001))

        #expect(!viewModel.isDrawnPolygonValid)
        #expect(viewModel.drawnAreaSqm == nil)
    }

    @Test("3점 이상 폴리곤은 면적을 만들고 진행 가능해진다")
    func validPolygonEnablesProceed() {
        let viewModel = makeViewModel()
        viewModel.selectedAddress = FarmLocationTestFixtures.sampleAddress()
        viewModel.beginDrawing()
        for coordinate in FarmLocationTestFixtures.squareCoordinates() {
            viewModel.addDrawnVertex(coordinate)
        }

        #expect(viewModel.isDrawnPolygonValid)
        if let area = viewModel.drawnAreaSqm {
            #expect(area > 9_900 && area < 10_100) // 약 100m×100m
        } else {
            Issue.record("drawnAreaSqm should be non-nil for a valid polygon")
        }
        #expect(viewModel.canProceed)
    }

    @Test("finishDrawing은 3점 미만이면 실패, 이상이면 성공하고 모드를 종료한다")
    func finishDrawingRequiresThreeVertices() async {
        let viewModel = makeViewModel()
        viewModel.beginDrawing()
        viewModel.addDrawnVertex(CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0))

        #expect(await viewModel.finishDrawing() == false)
        #expect(viewModel.isDrawingMode)

        for coordinate in FarmLocationTestFixtures.squareCoordinates() {
            viewModel.addDrawnVertex(coordinate)
        }

        #expect(await viewModel.finishDrawing() == true)
        #expect(!viewModel.isDrawingMode)
    }

    @Test("작도 제출 필드: PNU 없음, 경계=작도좌표, 수동면적, 사용자작도 출처, 좌표=중심")
    func drawnSubmissionFields() async {
        let viewModel = makeViewModel()
        viewModel.selectedAddress = FarmLocationTestFixtures.sampleAddress()
        viewModel.beginDrawing()
        let square = FarmLocationTestFixtures.squareCoordinates()
        for coordinate in square {
            viewModel.addDrawnVertex(coordinate)
        }
        await viewModel.finishDrawing()

        #expect(viewModel.submissionPNU == nil)
        #expect(viewModel.submissionLandCategory == nil)
        #expect(viewModel.submissionBoundaryCoordinates.count == square.count)
        #expect(viewModel.submissionAreaIsManualEntry)
        #expect(viewModel.submissionDataSource == .onboardingUserDrawnPolygon)

        let expectedCentroid = FarmlandParcel.centroid(of: square.map { GeoPoint($0) })
        #expect(expectedCentroid != nil)
        if let centroid = viewModel.submissionCoordinate, let expected = expectedCentroid {
            #expect(abs(centroid.latitude - expected.latitude) < 1e-9)
            #expect(abs(centroid.longitude - expected.longitude) < 1e-9)
        } else {
            Issue.record("submissionCoordinate should be the polygon centroid")
        }
    }

    @Test("필지 발견 시 제출 필드는 지적도 좌표/출처를 사용한다")
    func parcelSubmissionFields() async {
        let coordinates = FarmLocationTestFixtures.squareCoordinates().map { GeoPoint($0) }
        let parcel = FarmlandParcel(
            pnu: "1111100000",
            jibunAddr: "전북 전주시 완산구 테스트동 1",
            jimok: "전",
            areaSqm: 500,
            coordinates: coordinates
        )
        let viewModel = makeViewModel(parcel: parcel)

        await viewModel.selectAddress(FarmLocationTestFixtures.sampleAddress())

        #expect(viewModel.selectedParcel != nil)
        #expect(viewModel.lookupState == .loaded)
        #expect(viewModel.submissionPNU == "1111100000")
        #expect(viewModel.submissionAreaIsManualEntry == false)
        #expect(viewModel.submissionAreaSqm == 500)
        #expect(viewModel.submissionBoundaryCoordinates.count == coordinates.count)
        #expect(viewModel.submissionDataSource == .onboardingJusoVWorld)
    }

    @Test("주소를 다시 선택하면 진행 중이던 작도가 초기화된다")
    func selectingAddressResetsDrawing() async {
        let viewModel = makeViewModel(parcel: nil)
        viewModel.beginDrawing()
        viewModel.addDrawnVertex(CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0))

        await viewModel.selectAddress(FarmLocationTestFixtures.sampleAddress())

        #expect(!viewModel.isDrawingMode)
        #expect(viewModel.drawnCoordinates.isEmpty)
    }
}

@MainActor
@Suite("FarmLocationViewModel 주소-좌표 동기화(역지오코딩)")
struct FarmLocationAddressSyncTests {

    private func makeViewModel(parcel: FarmlandParcel? = nil, reverse: ReverseGeocodedAddress? = nil) -> FarmLocationViewModel {
        var stub = StubVWorld(parcel: parcel)
        if let reverse { stub.reverseAddress = reverse }
        return FarmLocationViewModel(
            addressSearch: StubAddressSearch(),
            vworld: stub,
            landCharacteristics: StubLandCharacteristics()
        )
    }

    @Test("Bug1: 검색 후 다른 좌표 탭 시 주소가 새 탭 기준으로 갱신된다")
    func mapTapAfterSearchRefreshesAddress() async {
        let parcel = FarmlandParcel(
            pnu: "4128000000", jibunAddr: "경기 남양주시 탭동 5", jimok: "전", areaSqm: 300, coordinates: []
        )
        let reverse = ReverseGeocodedAddress(roadAddress: "경기 남양주시 탭로 5", jibunAddress: "경기 남양주시 탭동 5")
        let viewModel = makeViewModel(parcel: parcel, reverse: reverse)

        await viewModel.selectAddress(FarmLocationTestFixtures.sampleAddress())
        // 검색 직후에는 검색으로 선택한 주소를 유지한다.
        #expect(viewModel.selectedAddress?.roadAddrPart1 == "전북 전주시 완산구 테스트로 1")

        await viewModel.handleMapTap(at: CLLocationCoordinate2D(latitude: 37.6, longitude: 127.1))

        // 탭 이후에는 주소가 새 위치의 역지오코딩 값으로 바뀐다(stale 방지).
        #expect(viewModel.selectedAddress?.roadAddrPart1 == "경기 남양주시 탭로 5")
        #expect(viewModel.selectedParcel?.pnu == "4128000000")
    }

    @Test("Bug2: 검색 없이 지도 탭만으로 주소가 채워지고 진행 가능해진다(필지 있음)")
    func mapTapWithoutSearchFillsAddress() async {
        let parcel = FarmlandParcel(
            pnu: "4165000000", jibunAddr: "경기 포천시 탭동 1", jimok: "전", areaSqm: 400, coordinates: []
        )
        let viewModel = makeViewModel(parcel: parcel)

        await viewModel.handleMapTap(at: CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0))

        #expect(viewModel.selectedAddress != nil)
        #expect(viewModel.canProceed)
    }

    @Test("Bug2: 필지 없어도 역지오코딩으로 주소가 채워지고 수동면적 입력 시 진행 가능")
    func mapTapWithoutParcelFillsAddressViaReverse() async {
        let viewModel = makeViewModel(parcel: nil)

        await viewModel.handleMapTap(at: CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0))

        #expect(viewModel.selectedParcel == nil)
        #expect(viewModel.selectedAddress != nil)
        #expect(!viewModel.canProceed) // 면적이 아직 없음

        viewModel.manualAreaText = "500"
        #expect(viewModel.canProceed)
    }

    @Test("Bug3: 폴리곤 작도 완료 시 중심 역지오코딩으로 주소가 채워진다")
    func finishDrawingFillsAddressViaReverse() async {
        let viewModel = makeViewModel(parcel: nil)
        viewModel.beginDrawing()
        for coordinate in FarmLocationTestFixtures.squareCoordinates() {
            viewModel.addDrawnVertex(coordinate)
        }

        let didFinish = await viewModel.finishDrawing()

        #expect(didFinish)
        #expect(viewModel.selectedAddress != nil)
        #expect(viewModel.canProceed)
    }

    @Test("도로명 NOT_FOUND + 지번 OK(필지 있음): 지번만 채워지고 진행 가능")
    func roadNotFoundFallsBackToJibun() async {
        let parcel = FarmlandParcel(
            pnu: "4165034000", jibunAddr: "경기도 포천시 관인면 중리 산 332", jimok: "전", areaSqm: 600, coordinates: []
        )
        // 산골 농지: 도로명 NOT_FOUND(nil). 지번은 GetFeature(필지)에서 채운다.
        let reverse = ReverseGeocodedAddress(roadAddress: nil, jibunAddress: nil)
        let viewModel = makeViewModel(parcel: parcel, reverse: reverse)

        await viewModel.handleMapTap(at: CLLocationCoordinate2D(latitude: 38.0, longitude: 127.2))

        #expect(viewModel.selectedAddress?.roadAddrPart1 == "")
        #expect(viewModel.selectedAddress?.jibunAddr == "경기도 포천시 관인면 중리 산 332")
        #expect(viewModel.canProceed)
    }
}

@Suite("FarmlandParcel geometry")
struct FarmlandParcelGeometryTests {

    @Test("planarArea는 100m×100m 사각형을 약 10,000㎡로 계산한다")
    func planarAreaOfSquare() {
        let coordinates = FarmLocationTestFixtures.squareCoordinates().map { GeoPoint($0) }
        let area = FarmlandParcel.planarArea(of: coordinates)
        #expect(area > 9_900 && area < 10_100)
    }

    @Test("centroid는 꼭짓점 평균 좌표를 반환한다")
    func centroidIsAverage() {
        let coordinates = [
            GeoPoint(latitude: 0, longitude: 0),
            GeoPoint(latitude: 0, longitude: 2),
            GeoPoint(latitude: 2, longitude: 2),
            GeoPoint(latitude: 2, longitude: 0)
        ]
        let centroid = FarmlandParcel.centroid(of: coordinates)
        #expect(centroid?.latitude == 1)
        #expect(centroid?.longitude == 1)
    }

    @Test("빈 좌표의 centroid는 nil이다")
    func centroidOfEmptyIsNil() {
        #expect(FarmlandParcel.centroid(of: []) == nil)
    }
}
