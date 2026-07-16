package com.chamchamcham.application.weather

import java.time.LocalDate

/**
 * 특정 날짜의 확정된 날씨. 기록 화면이 고른 날짜에 대해 돌려주는 값이다.
 *
 * 예보와 달리 최저/최고가 non-null이다. 셋 중 하나라도 없으면 그 날짜는 아예 조회 실패로
 * 처리한다(404) — 기록에 붙는 값이라 반쪽짜리를 남기면 나중에 구분할 수 없다.
 */
data class DailyWeather(
    val date: LocalDate,
    val condition: WeatherCondition,
    val minTemperature: Int,
    val maxTemperature: Int
)
