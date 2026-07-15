package com.chamchamcham.api.weather

import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 기상청(KMA)이 공개한 체감온도 공식을 그대로 구현한 순수 함수 객체.
 * 외부 API가 아닌, 이미 조회된 기온/습도/풍속으로부터 체감온도를 계산한다.
 * - 여름철(5~9월): 열지수 기반 공식(습구온도 Tw를 거쳐 계산).
 * - 겨울철(10~4월): 풍속냉각 공식.
 * 두 구간은 12개월을 정확히 양분하며, 그 외의 "제3의" 구간은 없다.
 */
object FeelsLikeTemperatureCalculator {
    private val SUMMER_MONTHS = setOf(5, 6, 7, 8, 9)

    fun of(temperature: Int, humidity: Int?, windSpeedMps: Double?, month: Int): Int? {
        val ta = temperature.toDouble()

        return if (month in SUMMER_MONTHS) {
            summer(ta, humidity)
        } else {
            winter(ta, windSpeedMps)
        }
    }

    // 겨울철 풍속냉각 공식. Ta<=10.0 && 풍속(m/s)>=1.3 일 때만 적용하며,
    // 조건을 벗어나면 원본 기온을 그대로 반환한다.
    private fun winter(ta: Double, windSpeedMps: Double?): Int? {
        if (windSpeedMps == null) return null
        if (!(ta <= 10.0 && windSpeedMps >= 1.3)) return ta.roundToInt()

        val v = windSpeedMps * 3.6
        val feelsLike = 13.12 + 0.6215 * ta - 11.37 * v.pow(0.16) + 0.3965 * v.pow(0.16) * ta
        return feelsLike.roundToInt()
    }

    // 여름철 열지수 공식. 검증되지 않은 조건은 없으며, 습도가 있으면 항상 적용한다.
    private fun summer(ta: Double, humidity: Int?): Int? {
        if (humidity == null) return null

        val rh = humidity.toDouble()
        val tw = stullWetBulbTemperature(ta, rh)
        val feelsLike = -0.2442 + 0.55399 * tw + 0.45535 * ta - 0.0022 * tw.pow(2) + 0.00278 * tw * ta + 3.0
        return feelsLike.roundToInt()
    }

    // Stull(2011) 습구온도 근사식. atan은 라디안 기준(kotlin.math.atan)을 그대로 사용한다.
    private fun stullWetBulbTemperature(ta: Double, rh: Double): Double {
        return ta * atan(0.151977 * sqrt(rh + 8.313659)) +
            atan(ta + rh) -
            atan(rh - 1.676331) +
            0.00391838 * rh.pow(1.5) * atan(0.023101 * rh) -
            4.686035
    }
}
