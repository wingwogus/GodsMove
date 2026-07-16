package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition

/**
 * 기상청 SKY(하늘상태)/PTY(강수형태) 코드를 [WeatherCondition]으로 매핑한다.
 * PTY가 강수 중이면 하늘상태보다 우선한다(기상청 관례).
 *
 * PTY 코드 공간은 소스마다 겹치지 않는다: PTY=4(소나기)는 단기예보(getVilageFcst)
 * 에만, PTY=5~7(빗방울/빗방울눈날림/눈날림)은 초단기실황(getUltraSrtNcst)에만
 * 나온다. 따라서 두 소스를 구분하지 않고 합집합 테이블 하나로 처리해도 안전하다.
 */
object SkyPtyConditionMapper {
    fun of(sky: String?, pty: String?): WeatherCondition {
        when (pty) {
            "1" -> return WeatherCondition.RAIN
            "2" -> return WeatherCondition.RAIN_SNOW
            "3" -> return WeatherCondition.SNOW
            "4" -> return WeatherCondition.SHOWER
            "5" -> return WeatherCondition.DRIZZLE
            "6" -> return WeatherCondition.DRIZZLE_SNOW
            "7" -> return WeatherCondition.SNOW_FLURRY
        }

        return when (sky) {
            "1" -> WeatherCondition.CLEAR
            "3" -> WeatherCondition.PARTLY_CLOUDY
            "4" -> WeatherCondition.CLOUDY
            else -> WeatherCondition.UNKNOWN
        }
    }
}
