//
//  WeatherModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// 기상청 SKY/PTY 코드를 감싸는 백엔드 `WeatherCondition` enum의 클라이언트 표현. `code`는 아이콘 매칭
/// (`WeatherIconMapping`)에, `text`는 화면 표시 및 기록 작성 시 `weatherCondition` 문구 전송에 쓴다.
struct WeatherCondition: Hashable, Sendable {
    let code: String
    let text: String
}

/// 홈 카드 / 기록 작성 화면의 "현재 날씨" (`GET /weather/home`).
struct CurrentWeather: Hashable, Sendable {
    let temperature: Int
    let condition: WeatherCondition
    let minTemperature: Int?
    let maxTemperature: Int?
}

/// 날씨 상세 화면 (`GET /weather/detail`, Figma `홈 -> 날씨 상세`). 선택 소스(체감/자외선/강수확률/습도/
/// 풍속) 중 조회에 실패한 값은 backend가 null로 내려주므로 전부 옵셔널 — 화면은 이를 "-"로 표시한다.
struct WeatherDetail: Sendable {
    let address: String
    let temperature: Int
    let feelsLikeTemperature: Int?
    let condition: WeatherCondition
    let minTemperature: Int?
    let maxTemperature: Int?
    let humidityPercent: Int?
    let windSpeedMps: Double?
    let precipitationProbabilityPercent: Int?
    let uvIndex: Int?
    let forecast: [WeatherForecastDay]
}

extension WeatherDetail {
    /// 기상청 공식 자외선지수 5단계(0~2 낮음/3~5 보통/6~7 높음/8~10 매우높음/11+ 위험). Figma·Business Rule
    /// 어디에도 구간표가 없어 기상청 공식 기준을 그대로 적용한다 — 별도 계약이 생기면 교체.
    var uvIndexLabel: String {
        guard let uvIndex else { return "-" }
        switch uvIndex {
        case ..<3: return "낮음"
        case 3..<6: return "보통"
        case 6..<8: return "높음"
        case 8..<11: return "매우높음"
        default: return "위험"
        }
    }
}

/// 주간예보 한 칸 (`ForecastResponse`). backend는 조회 실패한 날짜를 지어내지 않고 빼므로 개수가 5보다
/// 적을 수 있다 — "오늘"도 예외 없이 이 규칙을 따른다(단기예보 자체가 없으면 오늘도 빠질 수 있음).
struct WeatherForecastDay: Identifiable, Sendable {
    let id = UUID()
    let dayLabel: String
    let condition: WeatherCondition
    /// Figma 칸 하나엔 값이 하나뿐이라 그날의 최고 기온을 우선하고, 없으면 최저로 대체한다.
    let temperature: Int?
}
