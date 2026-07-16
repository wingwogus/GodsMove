//
//  WeatherRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// 홈 카드와 기록 작성 화면이 공유하는 날씨 조회. `Features/Home`과 `Features/Record` 양쪽에서 쓰여서
/// 두 피처 어디에도 속하지 않는 자기 자신의 피처로 뺐다 — 백엔드 `weather` 컨트롤러와 경계를 맞춘다.
protocol WeatherRepository: Sendable {
    func fetchHome(farmId: UUID?) async throws -> CurrentWeather
    func fetchDetail(farmId: UUID?) async throws -> WeatherDetail
}

struct RemoteWeatherRepository: WeatherRepository {
    let apiClient: APIClient

    func fetchHome(farmId: UUID?) async throws -> CurrentWeather {
        let dto: HomeWeatherResponseDTO = try await apiClient.send(WeatherEndpoint.home(farmId: farmId))
        return dto.toDomain()
    }

    func fetchDetail(farmId: UUID?) async throws -> WeatherDetail {
        let dto: WeatherDetailResponseDTO = try await apiClient.send(WeatherEndpoint.detail(farmId: farmId))
        return dto.toDomain()
    }
}
