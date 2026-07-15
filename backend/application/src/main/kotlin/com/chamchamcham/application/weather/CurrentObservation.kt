package com.chamchamcham.application.weather

import java.time.LocalDateTime

/**
 * 초단기실황(getUltraSrtNcst)의 실측값.
 *
 * [precipitationType]은 실황 PTY가 강수를 가리킬 때만 값이 있고, 강수가 없으면(PTY=0) null이다.
 * 그때 최종 날씨 상태는 단기예보의 SKY로 정한다 — 강수 여부는 실측이 가장 정확하지만
 * 하늘상태(맑음/구름많음/흐림)는 실황에 아예 없기 때문이다.
 */
data class CurrentObservation(
    val temperature: Int,
    val precipitationType: WeatherCondition?,
    val observedAt: LocalDateTime,
    val humidity: Int?,
    val windSpeed: Double?,
    val feelsLikeTemperature: Int?
)
