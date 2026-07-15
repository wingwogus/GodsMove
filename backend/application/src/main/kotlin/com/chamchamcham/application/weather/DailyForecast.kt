package com.chamchamcham.application.weather

import java.time.LocalDate

/**
 * 하루치 예보.
 *
 * 최저/최고는 발표시각에 따라 응답에 없을 수 있어 nullable이다. 없으면 null로 두고
 * 남은 시간대 기온으로 지어내지 않는다 — 그 폴백이 이전 구현에서 하루 21시간 동안
 * 틀린 최저/최고를 조용히 내보낸 원인이었다.
 */
data class DailyForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val minTemperature: Int?,
    val maxTemperature: Int?
)
