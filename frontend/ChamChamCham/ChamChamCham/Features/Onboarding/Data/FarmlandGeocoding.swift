//
//  FarmlandGeocoding.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import CoreLocation

protocol FarmlandGeocoding: Sendable {
    func geocode(roadAddress: String) async throws -> CLLocationCoordinate2D
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
