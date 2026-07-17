//
//  LocationManager.swift
//  ChamChamCham
//
//  Created by iyungui on 7/17/26.
//

import CoreLocation
import Observation

/// 지도 화면의 "현재 위치" 표시/초기 카메라 포커스를 위한 위치 권한 래퍼.
///
/// 지도가 처음 등장할 때 `requestAuthorization()`을 호출해 권한 alert를 띄우고,
/// 승인되면 1회성 위치(`requestLocation`)로 `lastCoordinate`를 채운다. 거부/실패 시에는
/// 호출 측이 서울 좌표로 폴백한다(여기서는 상태만 게시한다).
///
/// `CLLocationManagerDelegate` 콜백은 매니저를 생성한 메인 런루프에서 호출되므로
/// `nonisolated` 델리게이트 메서드 안에서 `MainActor.assumeIsolated`로 상태를 갱신한다.
@Observable
@MainActor
final class LocationManager: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()

    private(set) var authorizationStatus: CLAuthorizationStatus
    private(set) var lastCoordinate: GeoPoint?

    var isAuthorized: Bool {
        authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways
    }

    override init() {
        authorizationStatus = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    /// 권한 상태에 맞춰 요청/위치조회를 시작한다. 지도 첫 등장 시 호출.
    func requestAuthorization() {
        switch authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        default:
            break
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // CLLocationManager는 Sendable이 아니므로 값(status)만 넘기고, 요청은 self.manager로 한다.
        let status = manager.authorizationStatus
        MainActor.assumeIsolated {
            authorizationStatus = status
            if isAuthorized {
                self.manager.requestLocation()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else { return }
        MainActor.assumeIsolated {
            lastCoordinate = GeoPoint(coordinate)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // 위치 조회 실패는 서울 폴백으로 처리되므로 상태만 유지한다.
    }
}
