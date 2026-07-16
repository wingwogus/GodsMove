package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.CurrentObservation
import com.chamchamcham.application.weather.CurrentObservationPort
import com.chamchamcham.application.weather.WeatherCondition
import com.chamchamcham.application.weather.WeatherLocation
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * 초단기실황(getUltraSrtNcst) 어댑터. 유일한 필수 소스라 데이터 부재는 전부
 * WEATHER_002로 실패시킨다(계획 §2 홈/상세 표, §5 부분 실패 정책).
 */
@Component
class KmaCurrentObservationAdapter(
    private val kmaApiClient: KmaApiClient,
    private val kmaBaseTime: KmaBaseTime,
    private val properties: KmaProperties
) : CurrentObservationPort {

    override fun fetch(location: WeatherLocation): CurrentObservation {
        val grid = GeoToGridConverter.convert(location.latitude, location.longitude)
        val baseDateTime = kmaBaseTime.resolveNcst()

        val params = linkedMapOf(
            "numOfRows" to "100",
            "base_date" to baseDateTime.baseDate,
            "base_time" to baseDateTime.baseTime,
            "nx" to grid.nx.toString(),
            "ny" to grid.ny.toString()
        )

        val items = kmaApiClient.getItems(
            properties.baseUrl.vilageFcst,
            "getUltraSrtNcst",
            params,
            NcstItemDto::class.java
        )
        // 필수 소스라 데이터 부재는 실패다(03/99는 KmaApiClient가 이미 빈 리스트로 흡수했다).
        if (items.isEmpty()) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        val valueByCategory = items.associate { it.category to it.obsrValue }

        val temperature = valueByCategory["T1H"]?.toDoubleOrNull()?.roundToInt()
            ?: throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        val humidity = valueByCategory["REH"]?.toDoubleOrNull()?.roundToInt()
        val windSpeed = valueByCategory["WSD"]?.toDoubleOrNull()
        val precipitationType = resolvePrecipitationType(valueByCategory["PTY"])
        val observedAt = resolveObservedAt(items)

        return CurrentObservation(
            temperature = temperature,
            precipitationType = precipitationType,
            observedAt = observedAt,
            humidity = humidity,
            windSpeed = windSpeed,
            feelsLikeTemperature = FeelsLikeTemperatureCalculator.of(
                temperature = temperature,
                humidity = humidity,
                windSpeedMps = windSpeed,
                month = observedAt.monthValue
            )
        )
    }

    // PTY=0(강수없음)이면 null. 매핑 결과가 UNKNOWN(알 수 없는 코드)이어도 null로 둔다 —
    // "강수 있는데 종류 모름"보다 "강수 정보 없음"이 정직하다(계획 지시).
    private fun resolvePrecipitationType(pty: String?): WeatherCondition? {
        if (pty == null || pty == "0") return null
        return SkyPtyConditionMapper.of(sky = null, pty = pty).takeUnless { it == WeatherCondition.UNKNOWN }
    }

    private fun resolveObservedAt(items: List<NcstItemDto>): LocalDateTime {
        val item = items.first()
        val baseDate = item.baseDate
        val baseTime = item.baseTime
        if (baseDate == null || baseTime == null) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }
        return try {
            LocalDateTime.parse("$baseDate$baseTime", OBSERVED_AT_FORMAT)
        } catch (exception: DateTimeParseException) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }
    }

    private data class NcstItemDto(
        @JsonProperty("baseDate") val baseDate: String? = null,
        @JsonProperty("baseTime") val baseTime: String? = null,
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("obsrValue") val obsrValue: String? = null
    )

    companion object {
        private val OBSERVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    }
}
