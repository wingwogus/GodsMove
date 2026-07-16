//
//  WeatherEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// `farmId`를 생략하면 백엔드가 회원의 첫 등록 농지로 해석한다 — 홈 카드는 항상 생략해서 부르고,
/// 기록 작성 화면은 사용자가 고른 농지를 명시해서 부른다.
enum WeatherEndpoint: Endpoint {
    case home(farmId: UUID?)
    case detail(farmId: UUID?)

    var path: String {
        switch self {
        case .home:
            "api/v1/weather/home"
        case .detail:
            "api/v1/weather/detail"
        }
    }

    var method: HTTPMethod { .get }

    var body: (any Encodable & Sendable)? { nil }

    var requiresAuth: Bool { true }

    var queryItems: [URLQueryItem] {
        switch self {
        case let .home(farmId), let .detail(farmId):
            guard let farmId else { return [] }
            return [URLQueryItem(name: "farmId", value: farmId.uuidString)]
        }
    }
}
