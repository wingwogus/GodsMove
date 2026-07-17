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
    func finishDrawingRequiresThreeVertices() {
        let viewModel = makeViewModel()
        viewModel.beginDrawing()
        viewModel.addDrawnVertex(CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0))

        #expect(viewModel.finishDrawing() == false)
        #expect(viewModel.isDrawingMode)

        for coordinate in FarmLocationTestFixtures.squareCoordinates() {
            viewModel.addDrawnVertex(coordinate)
        }

        #expect(viewModel.finishDrawing() == true)
        #expect(!viewModel.isDrawingMode)
    }

    @Test("작도 제출 필드: PNU 없음, 경계=작도좌표, 수동면적, 사용자작도 출처, 좌표=중심")
    func drawnSubmissionFields() {
        let viewModel = makeViewModel()
        viewModel.selectedAddress = FarmLocationTestFixtures.sampleAddress()
        viewModel.beginDrawing()
        let square = FarmLocationTestFixtures.squareCoordinates()
        for coordinate in square {
            viewModel.addDrawnVertex(coordinate)
        }
        viewModel.finishDrawing()

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
