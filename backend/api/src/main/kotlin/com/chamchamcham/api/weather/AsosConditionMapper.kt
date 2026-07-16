package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition

/**
 * ASOS 일자료엔 날씨 상태 필드가 없어, 평균 전운량(avgTca, 0~10)과 일강수량(sumRn)
 * 으로 근사한다. 이건 불가피한 정확도 한계다.
 *
 * 무강수인 날은 sumRn이 빈 문자열로 내려오고, 호출부의 toDoubleOrNull()이 이를
 * null로 만든다(실측 확인됨) — 그래서 sumRn==null도 "비 아님"으로 취급한다.
 */
object AsosConditionMapper {
    fun of(avgTca: Double?, sumRn: Double?): WeatherCondition {
        if (sumRn != null && sumRn > 0.0) return WeatherCondition.RAIN

        return when {
            avgTca == null -> WeatherCondition.UNKNOWN
            avgTca <= 2.0 -> WeatherCondition.CLEAR
            avgTca <= 7.0 -> WeatherCondition.PARTLY_CLOUDY
            else -> WeatherCondition.CLOUDY
        }
    }
}
