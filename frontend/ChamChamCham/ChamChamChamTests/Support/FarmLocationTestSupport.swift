//
//  FarmLocationTestSupport.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/17/26.
//

import CoreLocation
@testable import ChamChamCham

/// 온보딩/마이페이지 재배지 엔진(`FarmLocationViewModel`)을 네트워크 없이 구동하기 위한 테스트 더블.

struct StubAddressSearch: AddressSearching {
    var results: [JusoAddress] = []
    func search(keyword: String) async throws -> [JusoAddress] { results }
}

struct StubVWorld: FarmlandGeocoding, ParcelLookup, ReverseGeocoding {
    var coordinate = CLLocationCoordinate2D(latitude: 37.5, longitude: 127.0)
    /// nil이면 `fetchParcel`이 `noParcelFound`를 던져 지적도 없는 상황을 재현한다.
    var parcel: FarmlandParcel?
    /// 역지오코딩 기본값은 non-nil이라 기존 테스트가 그대로 통과한다. NOT_FOUND(도로명/지번 없음)
    /// 케이스는 `roadAddress`/`jibunAddress`를 nil로 override해 검증한다.
    var reverseAddress = ReverseGeocodedAddress(
        roadAddress: "전북 전주시 완산구 역지오코딩로 1",
        jibunAddress: "전북 전주시 완산구 역지오코딩동 1"
    )

    func geocode(roadAddress: String) async throws -> CLLocationCoordinate2D { coordinate }

    func fetchParcel(at coordinate: CLLocationCoordinate2D) async throws -> FarmlandParcel {
        guard let parcel else { throw FarmLocationAPIError.noParcelFound }
        return parcel
    }

    func reverseGeocode(at coordinate: CLLocationCoordinate2D) async throws -> ReverseGeocodedAddress {
        reverseAddress
    }
}

struct StubLandCharacteristics: LandCharacteristicsLookup {
    var info: LandCharacteristics?
    func fetchLandCharacteristics(pnu: String) async throws -> LandCharacteristics {
        guard let info else { throw FarmLocationAPIError.noResult }
        return info
    }
}

enum FarmLocationTestFixtures {
    /// 위도 37.5 근처의 약 100m × 100m 정사각형 꼭짓점.
    static func squareCoordinates() -> [CLLocationCoordinate2D] {
        let lat0 = 37.5
        let lon0 = 127.0
        let dLat = 100.0 / 111_320.0
        let dLon = 100.0 / (111_320.0 * cos(lat0 * .pi / 180))
        return [
            CLLocationCoordinate2D(latitude: lat0, longitude: lon0),
            CLLocationCoordinate2D(latitude: lat0, longitude: lon0 + dLon),
            CLLocationCoordinate2D(latitude: lat0 + dLat, longitude: lon0 + dLon),
            CLLocationCoordinate2D(latitude: lat0 + dLat, longitude: lon0)
        ]
    }

    static func sampleAddress() -> JusoAddress {
        JusoAddress(roadAddrPart1: "전북 전주시 완산구 테스트로 1",
                    jibunAddr: "전북 전주시 완산구 테스트동 1",
                    bdNm: "")
    }
}
