package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.ShortTermForecast
import com.chamchamcham.application.weather.ShortTermForecastPort
import com.chamchamcham.application.weather.WeatherCondition
import com.chamchamcham.application.weather.WeatherLocation
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * 단기예보(getVilageFcst) 어댑터. 계획 §2.1대로 같은 operation을 두 용도로 나눠 부른다:
 * [fetchLatest]는 현재 하늘상태·강수확률·D1~D4용, [fetchTodayRange]는 당일 TMN/TMX 전용이다.
 * 둘 다 선택 소스라 데이터 부재는 예외가 아니라 null이다.
 */
@Component
class KmaShortTermForecastAdapter(
    private val kmaApiClient: KmaApiClient,
    private val kmaBaseTime: KmaBaseTime,
    private val properties: KmaProperties,
    private val clock: Clock
) : ShortTermForecastPort {

    override fun fetchLatest(location: WeatherLocation): ShortTermForecast? {
        val items = fetchItems(location, kmaBaseTime.resolveLatest())
        if (items.isEmpty()) return null

        val now = LocalDateTime.now(clock)
        val nearestSlot = nearestSlot(items, now)

        val currentSky = nearestSlot
            ?.let { slot -> valueAtSlot(items, slot, "SKY") }
            ?.let { sky -> SkyPtyConditionMapper.of(sky = sky, pty = null) }
            ?: WeatherCondition.UNKNOWN

        val precipitationProbability = nearestSlot
            ?.let { slot -> valueAtSlot(items, slot, "POP") }
            ?.toIntOrNull()

        val dailyForecasts = items
            .filter { it.fcstDate != null }
            .groupBy { it.fcstDate!! }
            .toSortedMap()
            .map { (fcstDate, dayItems) -> buildDailyForecast(fcstDate, dayItems) }

        return ShortTermForecast(
            currentSky = currentSky,
            precipitationProbability = precipitationProbability,
            dailyForecasts = dailyForecasts
        )
    }

    override fun fetchTodayRange(location: WeatherLocation): DailyForecast? {
        val items = fetchItems(location, kmaBaseTime.resolveTodayRange())
        val today = LocalDate.now(clock).format(DATE_FORMAT)
        val todayItems = items.filter { it.fcstDate == today }
        if (todayItems.isEmpty()) return null
        return buildDailyForecast(today, todayItems)
    }

    private fun fetchItems(location: WeatherLocation, baseDateTime: KmaBaseDateTime): List<VilageFcstItemDto> {
        val grid = GeoToGridConverter.convert(location.latitude, location.longitude)
        val params = linkedMapOf(
            "numOfRows" to "1000",
            "base_date" to baseDateTime.baseDate,
            "base_time" to baseDateTime.baseTime,
            "nx" to grid.nx.toString(),
            "ny" to grid.ny.toString()
        )
        return kmaApiClient.getItems(properties.baseUrl.vilageFcst, "getVilageFcst", params, VilageFcstItemDto::class.java)
    }

    // 🔴 날짜로 먼저 거르지 않고 fcstDate+fcstTime 전체에서 최근접을 찾는다. 23시 발표엔
    // 당일 항목이 0개라(계획 §1) 날짜로 필터링하면 그 구간이 통째로 빈다 — 그때 가장 가까운
    // 건 다음날 00시 슬롯이고, 30분 뒤 예보라 오히려 정확하다.
    private fun nearestSlot(items: List<VilageFcstItemDto>, now: LocalDateTime): LocalDateTime? {
        return items.mapNotNull { parseFcstDateTime(it) }
            .distinct()
            .minByOrNull { slot -> Duration.between(now, slot).abs() }
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

    private fun valueAtSlot(items: List<VilageFcstItemDto>, slot: LocalDateTime, category: String): String? {
        return items.firstOrNull { it.category == category && parseFcstDateTime(it) == slot }?.fcstValue
    }

    // TMN/TMX가 없으면 null이다. 절대 TMP 값들의 min/max로 폴백하지 않는다 — 그 폴백이
    // 이전 구현에서 하루 21시간 동안 틀린 최저/최고를 조용히 내보낸 원인이었다(계획 §3).
    private fun buildDailyForecast(fcstDate: String, items: List<VilageFcstItemDto>): DailyForecast {
        val date = LocalDate.parse(fcstDate, DATE_FORMAT)
        val minTemperature = valueOfCategory(items, "TMN")?.toDoubleOrNull()?.roundToInt()
        val maxTemperature = valueOfCategory(items, "TMX")?.toDoubleOrNull()?.roundToInt()
        return DailyForecast(
            date = date,
            condition = noonCondition(items),
            minTemperature = minTemperature,
            maxTemperature = maxTemperature
        )
    }

    private fun valueOfCategory(items: List<VilageFcstItemDto>, category: String): String? =
        items.firstOrNull { it.category == category }?.fcstValue

    private fun noonCondition(items: List<VilageFcstItemDto>): WeatherCondition {
        val noonTime = items.mapNotNull { it.fcstTime }
            .distinct()
            .mapNotNull { time -> parseTime(time)?.let { time to it } }
            .minByOrNull { (_, parsed) -> Duration.between(parsed, LocalTime.NOON).abs() }
            ?.first
            ?: return WeatherCondition.UNKNOWN
        val sky = items.firstOrNull { it.category == "SKY" && it.fcstTime == noonTime }?.fcstValue
        val pty = items.firstOrNull { it.category == "PTY" && it.fcstTime == noonTime }?.fcstValue
        return SkyPtyConditionMapper.of(sky = sky, pty = pty)
    }

    private fun parseTime(time: String): LocalTime? = try {
        LocalTime.parse(time, TIME_FORMAT)
    } catch (exception: DateTimeParseException) {
        null
    }

    private data class VilageFcstItemDto(
        @JsonProperty("baseDate") val baseDate: String? = null,
        @JsonProperty("baseTime") val baseTime: String? = null,
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("fcstDate") val fcstDate: String? = null,
        @JsonProperty("fcstTime") val fcstTime: String? = null,
        @JsonProperty("fcstValue") val fcstValue: String? = null
    )

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm")
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    }
}
