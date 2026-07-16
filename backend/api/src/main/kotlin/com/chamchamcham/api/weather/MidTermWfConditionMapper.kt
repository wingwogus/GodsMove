package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition

/**
 * 중기예보(getMidLandFcst) `wf` 필드는 자유 텍스트다("흐리고 비", "구름많음" 등).
 * 강수 표현이 있으면 하늘상태보다 우선 검사한다(순서가 중요 — "흐리고 비/눈"이
 * RAIN_SNOW여야지 RAIN이나 CLOUDY로 먼저 걸리면 안 된다).
 *
 * "흐림"을 "흐리"로 매칭하면 실패한다("흐림"은 "흐리"를 부분문자열로 포함하지
 * 않는다) — 실제로 있었던 버그라 "흐"로 검사한다.
 */
object MidTermWfConditionMapper {
    fun of(wf: String?): WeatherCondition {
        if (wf.isNullOrBlank()) return WeatherCondition.UNKNOWN

        return when {
            wf.contains("비/눈") -> WeatherCondition.RAIN_SNOW
            wf.contains("소나기") -> WeatherCondition.SHOWER
            wf.contains("비") -> WeatherCondition.RAIN
            wf.contains("눈") -> WeatherCondition.SNOW
            wf.contains("흐") -> WeatherCondition.CLOUDY
            wf.contains("구름많") -> WeatherCondition.PARTLY_CLOUDY
            wf.contains("맑") -> WeatherCondition.CLEAR
            else -> WeatherCondition.UNKNOWN
        }
    }
}
