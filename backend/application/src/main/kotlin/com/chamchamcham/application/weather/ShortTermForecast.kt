package com.chamchamcham.application.weather

/**
 * 단기예보(getVilageFcst) 최신 발표에서 뽑은 것.
 *
 * [currentSky]와 [precipitationProbability]는 현재 시각에 가장 가까운 예보 시각의 값이고,
 * 그 시각은 날짜 경계를 넘어갈 수 있다 — 23시 발표에는 당일 항목이 아예 없어서 다음날 00시가
 * 가장 가깝다. 날짜로 걸러내면 그 구간이 빈다.
 */
data class ShortTermForecast(
    val currentSky: WeatherCondition,
    val precipitationProbability: Int?,
    val dailyForecasts: List<DailyForecast>
)
