//
//  WeatherDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ConditionResponseDTO: Decodable, Sendable {
    let code: String
    let text: String

    func toDomain() -> WeatherCondition { WeatherCondition(code: code, text: text) }
}

struct HomeWeatherResponseDTO: Decodable, Sendable {
    let temperature: Int
    let condition: ConditionResponseDTO
    let minTemperature: Int?
    let maxTemperature: Int?

    func toDomain() -> CurrentWeather {
        CurrentWeather(
            temperature: temperature,
            condition: condition.toDomain(),
            minTemperature: minTemperature,
            maxTemperature: maxTemperature
        )
    }
}

/// `DetailResponse.current` (`CurrentResponse`).
struct CurrentConditionResponseDTO: Decodable, Sendable {
    let temperature: Int
    let feelsLikeTemperature: Int?
    let condition: ConditionResponseDTO
    let minTemperature: Int?
    let maxTemperature: Int?
    let humidity: Int?
    let windSpeed: Double?
    let precipitationProbability: Int?
    let uvIndex: Int?
}

/// `ForecastResponse`. `date`는 `yyyy-MM-dd`(로케일 없는 LocalDate) 그대로 문자열로 들고 있다가
/// 오늘 날짜 문자열과 직접 비교한다 — `Date` 파싱/타임존 왕복 없이 "오늘" 판정을 끝낼 수 있어서다.
struct ForecastResponseDTO: Decodable, Sendable {
    let date: String
    let dayOfWeek: String
    let condition: ConditionResponseDTO
    let minTemperature: Int?
    let maxTemperature: Int?

    func toDomain() -> WeatherForecastDay {
        WeatherForecastDay(
            dayLabel: WeatherDayLabel.label(date: date, dayOfWeek: dayOfWeek),
            condition: condition.toDomain(),
            temperature: maxTemperature ?? minTemperature
        )
    }
}

struct WeatherDetailResponseDTO: Decodable, Sendable {
    let address: String
    let current: CurrentConditionResponseDTO
    let forecast: [ForecastResponseDTO]

    func toDomain() -> WeatherDetail {
        WeatherDetail(
            address: address,
            temperature: current.temperature,
            feelsLikeTemperature: current.feelsLikeTemperature,
            condition: current.condition.toDomain(),
            minTemperature: current.minTemperature,
            maxTemperature: current.maxTemperature,
            humidityPercent: current.humidity,
            windSpeedMps: current.windSpeed,
            precipitationProbabilityPercent: current.precipitationProbability,
            uvIndex: current.uvIndex,
            forecast: forecast.map { $0.toDomain() }
        )
    }
}

/// `ForecastResponse.date`/`dayOfWeek` → Figma 주간예보 라벨("오늘"/"월"~"일").
enum WeatherDayLabel {
    static func label(date: String, dayOfWeek: String) -> String {
        guard date != todayDateString else { return "오늘" }
        switch dayOfWeek {
        case "MONDAY": return "월"
        case "TUESDAY": return "화"
        case "WEDNESDAY": return "수"
        case "THURSDAY": return "목"
        case "FRIDAY": return "금"
        case "SATURDAY": return "토"
        case "SUNDAY": return "일"
        default: return "-"
        }
    }

    private static var todayDateString: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
