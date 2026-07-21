//
//  FarmlandGeocoding.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import CoreLocation

protocol FarmlandGeocoding: Sendable {
    /// 도로명 주소로 좌표 변환을 시도하고, 실패하거나 도로명이 없으면 지번 주소로 재시도한다.
    ///
    /// 산골·하천변 농지 등은 도로명이 없어 `roadAddress`가 빈 문자열인 경우가 많다. 이때
    /// 지번(`jibunAddress`)으로 폴백해야 좌표를 얻을 수 있다. 둘 다 실패하면 throw한다.
    func geocode(roadAddress: String, jibunAddress: String) async throws -> CLLocationCoordinate2D
}

protocol ParcelLookup: Sendable {
    func fetchParcel(at coordinate: CLLocationCoordinate2D) async throws -> FarmlandParcel
}

/// 좌표→주소 역지오코딩 결과.
///
/// 하천둑·산골 농지처럼 도로명 주소가 없는 곳은 `roadAddress`가 nil이고
/// `jibunAddress`만 채워진다("도로명이 없음"과 "요청 실패"를 구분하기 위해,
/// 실패는 throw로, 없음은 nil 필드로 표현한다).
struct ReverseGeocodedAddress: Sendable {
    let roadAddress: String?
    let jibunAddress: String?
}

protocol ReverseGeocoding: Sendable {
    func reverseGeocode(at coordinate: CLLocationCoordinate2D) async throws -> ReverseGeocodedAddress
}
