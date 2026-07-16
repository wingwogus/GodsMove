//
//  WeatherModelsTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("WeatherDayLabel")
struct WeatherDayLabelTests {
    /// 실행 시점의 "오늘"과 절대 겹치지 않는 고정 과거 날짜 — 요일 매핑만 검증할 때 쓴다.
    private static let notToday = "2020-01-01"

    @Test("오늘 날짜는 dayOfWeek와 무관하게 '오늘'로 표시된다")
    func today() {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        let todayString = formatter.string(from: Date())

        #expect(WeatherDayLabel.label(date: todayString, dayOfWeek: "MONDAY") == "오늘")
    }

    @Test("과거/미래 날짜는 dayOfWeek을 한글 한 글자로 변환한다", arguments: [
        ("MONDAY", "월"), ("TUESDAY", "화"), ("WEDNESDAY", "수"), ("THURSDAY", "목"),
        ("FRIDAY", "금"), ("SATURDAY", "토"), ("SUNDAY", "일")
    ])
    func weekday(dayOfWeek: String, expected: String) {
        #expect(WeatherDayLabel.label(date: Self.notToday, dayOfWeek: dayOfWeek) == expected)
    }

    @Test("알 수 없는 dayOfWeek는 대시로 대체한다")
    func unknown() {
        #expect(WeatherDayLabel.label(date: Self.notToday, dayOfWeek: "UNKNOWN") == "-")
    }
}

@Suite("WeatherDetail uvIndexLabel")
struct WeatherDetailUVIndexLabelTests {
    private func detail(uvIndex: Int?) -> WeatherDetail {
        WeatherDetail(
            address: "",
            temperature: 20,
            feelsLikeTemperature: nil,
            condition: WeatherCondition(code: "CLEAR", text: "맑음"),
            minTemperature: nil,
            maxTemperature: nil,
            humidityPercent: nil,
            windSpeedMps: nil,
            precipitationProbabilityPercent: nil,
            uvIndex: uvIndex,
            forecast: []
        )
    }

    @Test("기상청 5단계 구간에 맞는 라벨을 반환한다", arguments: [
        (0, "낮음"), (2, "낮음"), (3, "보통"), (5, "보통"),
        (6, "높음"), (7, "높음"), (8, "매우높음"), (10, "매우높음"), (11, "위험"), (15, "위험")
    ])
    func thresholds(uvIndex: Int, expected: String) {
        #expect(detail(uvIndex: uvIndex).uvIndexLabel == expected)
    }

    @Test("자외선지수가 없으면 대시를 반환한다")
    func missing() {
        #expect(detail(uvIndex: nil).uvIndexLabel == "-")
    }
}
