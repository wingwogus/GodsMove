package com.chamchamcham.api.weather

import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackCurrentWeather
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackForecastDay
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackLiveWeather
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackWeatherPort
import com.chamchamcham.application.weather.CurrentObservationPort
import com.chamchamcham.application.weather.WeatherCondition
import com.chamchamcham.application.weather.WeatherLocation
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 코칭(기록 피드백)이 쓰는 날씨 포트. 옛 KmaWeatherProvider.fetch()의 집계·위험플래그 로직을
 * 새 아키텍처(KmaApiClient/CurrentObservationPort) 위로 이관했다 — 임계값·필드는 동일하게
 * 유지해 RecordFeedbackPromptBuilder와의 계약을 깨지 않는다.
 */
@Component
class RecordFeedbackKmaWeatherAdapter(
    private val currentObservationPort: CurrentObservationPort,
    private val kmaApiClient: KmaApiClient,
    private val kmaBaseTime: KmaBaseTime,
    private val properties: KmaProperties,
    private val clock: Clock
) : RecordFeedbackWeatherPort {

    override fun fetch(latitude: Double, longitude: Double, limitDays: Int): RecordFeedbackLiveWeather {
        require(limitDays in 1..7) { "limitDays must be between 1 and 7" }

        // RecordFeedbackWeatherPort는 위경도만 넘겨준다 — 주소/PNU는 record-feedback 쪽에 없고,
        // 여기서 재사용하는 실황 조회(CurrentObservationPort)도 위경도만 쓰므로 빈 값으로 채운다.
        val location = WeatherLocation(latitude = latitude, longitude = longitude, roadAddress = "", pnu = null)
        val current = currentObservationPort.fetch(location)
        val items = fetchVilageFcstItems(latitude, longitude)

        val today = LocalDate.now(clock)
        // 강수는 실황 실측(current.precipitationType)이 우선이고, 강수가 없으면 동네예보 SKY로
        // 보완한다 — FarmWeatherService.resolveCondition과 같은 우선순위.
        val currentSky = current.precipitationType ?: nearestSky(items) ?: WeatherCondition.UNKNOWN

        return RecordFeedbackLiveWeather(
            current = RecordFeedbackCurrentWeather(
                temperatureC = current.temperature,
                skyCondition = currentSky.text,
                observedAt = current.observedAt
            ),
            forecastDays = aggregateForecastDays(items, today, limitDays),
            source = KMA_SHORT_TERM_SOURCE
        )
    }

    private fun fetchVilageFcstItems(latitude: Double, longitude: Double): List<VilageFcstItemDto> {
        val grid = GeoToGridConverter.convert(latitude, longitude)
        val baseDateTime = kmaBaseTime.resolveLatest()
        val params = linkedMapOf(
            // 옛 KmaWeatherProvider.fetch()도 이 조회엔 2000을 썼다(다른 getVilageFcst 호출은 1000) —
            // 7일치 전 카테고리를 다 훑어야 해서 여유를 더 둔다.
            "numOfRows" to "2000",
            "base_date" to baseDateTime.baseDate,
            "base_time" to baseDateTime.baseTime,
            "nx" to grid.nx.toString(),
            "ny" to grid.ny.toString()
        )
        return kmaApiClient.getItems(properties.baseUrl.vilageFcst, "getVilageFcst", params, VilageFcstItemDto::class.java)
    }

    private fun nearestSky(items: List<VilageFcstItemDto>): WeatherCondition? {
        val now = LocalDateTime.now(clock)
        val nearestSlot = items.mapNotNull { parseFcstDateTime(it) }
            .distinct()
            .minByOrNull { slot -> Duration.between(now, slot).abs() }
            ?: return null
        val sky = items.firstOrNull { it.category == "SKY" && parseFcstDateTime(it) == nearestSlot }?.fcstValue
        return SkyPtyConditionMapper.of(sky = sky, pty = null).takeUnless { it == WeatherCondition.UNKNOWN }
    }

    private fun parseFcstDateTime(item: VilageFcstItemDto): LocalDateTime? {
        val date = item.fcstDate ?: return null
        val time = item.fcstTime ?: return null
        return try {
            LocalDateTime.parse("$date$time", DATE_TIME_FORMAT)
        } catch (exception: DateTimeParseException) {
            null
        }
    }

    private fun aggregateForecastDays(
        items: List<VilageFcstItemDto>,
        today: LocalDate,
        limitDays: Int
    ): List<RecordFeedbackForecastDay> {
        return items
            .mapNotNull { item ->
                val date = item.fcstDate?.let(::parseForecastDate) ?: return@mapNotNull null
                if (date < today) return@mapNotNull null
                date to item
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .entries
            .take(limitDays)
            .map { (date, dayItems) -> aggregateForecastDay(date, dayItems) }
    }

    private fun aggregateForecastDay(date: LocalDate, items: List<VilageFcstItemDto>): RecordFeedbackForecastDay {
        val temperatures = items
            .filter { it.category == "TMP" || it.category == "TMN" || it.category == "TMX" }
            .mapNotNull { it.fcstValue?.toBigDecimalOrNull() }
        val maxTemperatureC = temperatures.maxOrNull()
        val minTemperatureC = temperatures.minOrNull()
        val rainProbabilityPct = items.maxIntValue("POP")
        val rainfallMm = items
            .filter { it.category == "PCP" }
            .mapNotNull { it.fcstValue?.toExactRainfallMmOrNull() }
            .maxOrNull()
        val humidityPct = items.maxDecimalValue("REH")
        val windSpeedMs = items.maxDecimalValue("WSD")
        val hasPrecipitationType = items
            .filter { it.category == "PTY" }
            .any { it.fcstValue?.toIntOrNull()?.let { value -> value != 0 } == true }

        return RecordFeedbackForecastDay(
            date = date,
            rainfallMm = rainfallMm,
            rainProbabilityPct = rainProbabilityPct,
            maxTemperatureC = maxTemperatureC,
            minTemperatureC = minTemperatureC,
            humidityPct = humidityPct,
            windSpeedMs = windSpeedMs,
            riskFlags = buildRiskFlags(
                hasPrecipitationType = hasPrecipitationType,
                rainProbabilityPct = rainProbabilityPct,
                rainfallMm = rainfallMm,
                maxTemperatureC = maxTemperatureC,
                humidityPct = humidityPct,
                windSpeedMs = windSpeedMs
            )
        )
    }

    private fun buildRiskFlags(
        hasPrecipitationType: Boolean,
        rainProbabilityPct: Int?,
        rainfallMm: BigDecimal?,
        maxTemperatureC: BigDecimal?,
        humidityPct: BigDecimal?,
        windSpeedMs: BigDecimal?
    ): List<String> {
        val flags = mutableListOf<String>()
        if (hasPrecipitationType || (rainProbabilityPct ?: 0) >= RAIN_PROBABILITY_THRESHOLD_PCT) flags += "RAIN"
        if (rainfallMm != null && rainfallMm >= HEAVY_RAIN_THRESHOLD_MM) flags += "HEAVY_RAIN"
        if (humidityPct != null && humidityPct >= HIGH_HUMIDITY_THRESHOLD_PCT) flags += "HIGH_HUMIDITY"
        if (maxTemperatureC != null && maxTemperatureC >= HOT_THRESHOLD_C) flags += "HOT"
        if (windSpeedMs != null && windSpeedMs >= STRONG_WIND_THRESHOLD_MS) flags += "STRONG_WIND"
        return flags
    }

    private data class VilageFcstItemDto(
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("fcstDate") val fcstDate: String? = null,
        @JsonProperty("fcstTime") val fcstTime: String? = null,
        @JsonProperty("fcstValue") val fcstValue: String? = null
    )

    companion object {
        private const val KMA_SHORT_TERM_SOURCE = "KMA_SHORT_TERM"
        private const val RAIN_PROBABILITY_THRESHOLD_PCT = 60
        private val FORECAST_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        private val EXACT_RAINFALL_PATTERN = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)\s*(?:mm)?\s*$""")
        private val HEAVY_RAIN_THRESHOLD_MM = BigDecimal("20")
        private val HIGH_HUMIDITY_THRESHOLD_PCT = BigDecimal("85")
        private val HOT_THRESHOLD_C = BigDecimal("30")
        private val STRONG_WIND_THRESHOLD_MS = BigDecimal("8")

        private fun parseForecastDate(value: String): LocalDate? =
            try {
                LocalDate.parse(value, FORECAST_DATE_FORMAT)
            } catch (exception: Exception) {
                null
            }

        private fun List<VilageFcstItemDto>.maxIntValue(category: String): Int? =
            filter { it.category == category }
                .mapNotNull { it.fcstValue?.toIntOrNull() }
                .maxOrNull()

        private fun List<VilageFcstItemDto>.maxDecimalValue(category: String): BigDecimal? =
            filter { it.category == category }
                .mapNotNull { it.fcstValue?.toBigDecimalOrNull() }
                .maxOrNull()

        private fun String.toExactRainfallMmOrNull(): BigDecimal? {
            val exactValue = EXACT_RAINFALL_PATTERN.matchEntire(this)?.groupValues?.get(1) ?: return null
            return exactValue.toBigDecimalOrNull()
        }
    }
}
