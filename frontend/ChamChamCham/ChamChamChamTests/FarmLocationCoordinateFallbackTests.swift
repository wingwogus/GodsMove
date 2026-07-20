//
//  FarmLocationCoordinateFallbackTests.swift
//  ChamChamCham
//
//  Created by iyungui on 7/20/26.
//

import CoreLocation
import Testing
@testable import ChamChamCham

/// 주소→좌표 변환 실패 시, 막다른 에러가 아니라 "지도에 직접 표시" 안내 폴백으로 전환되는지
/// 검증한다(App Store Guideline 2.1 리젝 대응). 도로명→지번 HTTP 폴백 자체는 `VWorldAPIService`가
/// `URLSession.shared`를 직접 사용해 주입점이 없어, 여기서는 ViewModel의 상태 전환·회복을 검증한다.
@MainActor
@Suite("FarmLocationViewModel 좌표변환 실패 폴백")
struct FarmLocationCoordinateFallbackTests {

    private func makeViewModel(geocodeError: FarmLocationAPIError?) -> FarmLocationViewModel {
        var stub = StubVWorld(parcel: nil)
        stub.geocodeError = geocodeError
        return FarmLocationViewModel(
            addressSearch: StubAddressSearch(),
            vworld: stub,
            landCharacteristics: StubLandCharacteristics()
        )
    }

    @Test("도로명·지번 모두 실패(noResult): 막다른 에러가 아니라 안내형 폴백 상태로 전환된다")
    func geocodeNoResultShowsGuidedFallback() async {
        let viewModel = makeViewModel(geocodeError: .noResult)

        await viewModel.selectAddress(FarmLocationTestFixtures.sampleAddress())

        // 막다른 .failed가 아니라 안내형 상태. noResult는 재시도해도 같으므로 retryable=false.
        #expect(viewModel.lookupState == .coordinateUnavailable(retryable: false))
        // 선택 주소는 유지해 사용자가 다루던 주소를 잃지 않는다.
        #expect(viewModel.selectedAddress != nil)
        #expect(viewModel.resolvedCoordinate == nil)
        // 좌표가 없으니 아직 진행 불가(백엔드가 위경도 필수).
        #expect(!viewModel.canProceed)
    }

    @Test("네트워크성 실패: 재시도 가능한 안내형 폴백 상태로 전환된다")
    func geocodeNetworkFailureIsRetryable() async {
        let viewModel = makeViewModel(geocodeError: .network("timeout"))

        await viewModel.selectAddress(FarmLocationTestFixtures.sampleAddress())

        #expect(viewModel.lookupState == .coordinateUnavailable(retryable: true))
        #expect(viewModel.selectedAddress != nil)
    }

    @Test("좌표변환 실패 후 지도에 직접 그리기로 진행 가능해진다(중심좌표 확보)")
    func drawingRecoversAfterCoordinateFailure() async {
        let viewModel = makeViewModel(geocodeError: .noResult)
        await viewModel.selectAddress(FarmLocationTestFixtures.sampleAddress())
        #expect(!viewModel.canProceed)

        viewModel.beginDrawing()
        for coordinate in FarmLocationTestFixtures.squareCoordinates() {
            viewModel.addDrawnVertex(coordinate)
        }
        await viewModel.finishDrawing()

        #expect(viewModel.canProceed)
        // 작도 중심좌표가 제출 좌표로 확보되어 백엔드(위경도 필수) 제출이 가능하다.
        #expect(viewModel.submissionCoordinate != nil)
    }

    @Test("좌표변환 성공 시에는 좌표가 해결되고 폴백 상태가 아니다")
    func geocodeSuccessResolvesCoordinate() async {
        let viewModel = makeViewModel(geocodeError: nil)

        await viewModel.selectAddress(FarmLocationTestFixtures.sampleAddress())

        #expect(viewModel.resolvedCoordinate != nil)
        if case .coordinateUnavailable = viewModel.lookupState {
            Issue.record("성공 시 coordinateUnavailable 상태여서는 안 된다")
        }
    }

    @Test("선택 주소가 없으면 retryCoordinate는 아무 동작도 하지 않는다")
    func retryCoordinateWithoutAddressNoops() async {
        let viewModel = makeViewModel(geocodeError: .noResult)

        await viewModel.retryCoordinate()

        #expect(viewModel.selectedAddress == nil)
        #expect(viewModel.lookupState == .idle)
    }
}
