package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackCurrentWeather
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackForecastDay
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackLiveWeather
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackWeatherPort
import com.chamchamcham.application.weather.WeatherProvider
import com.chamchamcham.application.weather.WeatherSnapshot
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 기상청(KMA) 초단기실황 + 초단기예보를 조합해 현재 날씨 스냅샷을 생성하는 어댑터.
 * - 기온은 초단기실황(getUltraSrtNcst)의 T1H에서.
 * - 하늘상태 문자열은 초단기예보(getUltraSrtFcst)의 SKY + PTY 조합에서.
 */
@Component
class KmaWeatherProvider internal constructor(
    private val restClient: RestClient,
    private val baseUrl: String,
    private val serviceKey: String,
    private val clock: Clock = Clock.systemDefaultZone()
) : WeatherProvider, RecordFeedbackWeatherPort {

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: WeatherKmaProperties
    ) : this(
        restClientBuilder
            .requestFactory(
                createRequestFactory(
                    properties.connectTimeoutMillis,
                    properties.readTimeoutMillis
                )
            )
            .build(),
        properties.baseUrl,
        properties.serviceKey
    )

    override fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherSnapshot {
        val grid = GeoToGridConverter.convert(latitude, longitude)
        val now = currentKmaTime()

        val ncst = requestItems(
            path = "getUltraSrtNcst",
            base = KmaBaseTimeResolver.resolveNcst(now),
            nx = grid.nx,
            ny = grid.ny
        )
        val fcst = requestItems(
            path = "getUltraSrtFcst",
            base = KmaBaseTimeResolver.resolveFcst(now),
            nx = grid.nx,
            ny = grid.ny
        )

        val temperature = ncst.firstOrNull { it.category == "T1H" }?.obsrValue
            ?.toDoubleOrNull()
            ?.roundToInt()
            ?: throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)

        val observedAt = parseObservedAt(ncst)
        val skyCondition = resolveSkyCondition(fcst)

        return WeatherSnapshot(
            temperature = temperature,
            skyCondition = skyCondition,
            observedAt = observedAt
        )
    }

    override fun fetch(latitude: Double, longitude: Double, limitDays: Int): RecordFeedbackLiveWeather {
        require(limitDays in 1..7) { "limitDays must be between 1 and 7" }

        val current = fetchCurrentWeather(latitude, longitude)
        val grid = GeoToGridConverter.convert(latitude, longitude)
        val now = currentKmaTime()
        val villageForecast = requestItems(
            path = "getVilageFcst",
            base = KmaBaseTimeResolver.resolveVilageFcst(now),
            nx = grid.nx,
            ny = grid.ny,
            numOfRows = VILAGE_FCST_NUM_OF_ROWS
        )

        return RecordFeedbackLiveWeather(
            current = RecordFeedbackCurrentWeather(
                temperatureC = current.temperature,
                skyCondition = current.skyCondition,
                observedAt = current.observedAt
            ),
            forecastDays = aggregateForecastDays(
                items = villageForecast,
                today = now.toLocalDate(),
                limitDays = limitDays
            ),
            source = KMA_SHORT_TERM_SOURCE
        )
    }

    private fun requestItems(
        path: String,
        base: KmaBaseDateTime,
        nx: Int,
        ny: Int,
        numOfRows: Int = DEFAULT_NUM_OF_ROWS
    ): List<KmaItem> {
        // serviceKey는 공공데이터포털 인코딩 키가 그대로 전달되도록 pre-encoded URI로 요청(이중 인코딩 방지).
        val uri = URI.create(
            "$baseUrl/$path" +
                "?serviceKey=$serviceKey" +
                "&pageNo=1&numOfRows=$numOfRows&dataType=JSON" +
                "&base_date=${base.baseDate}&base_time=${base.baseTime}" +
                "&nx=$nx&ny=$ny"
        )

        val response = try {
            restClient.get()
                .uri(uri)
                .retrieve()
                .body(KmaResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        val body = response?.response
        if (body?.header?.resultCode != "00") {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }
        return body.body?.items?.item ?: throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    private fun aggregateForecastDays(
        items: List<KmaItem>,
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

    private fun aggregateForecastDay(date: LocalDate, items: List<KmaItem>): RecordFeedbackForecastDay {
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
        if (hasPrecipitationType || (rainProbabilityPct ?: 0) >= 60) flags += "RAIN"
        if (rainfallMm != null && rainfallMm >= HEAVY_RAIN_THRESHOLD_MM) flags += "HEAVY_RAIN"
        if (humidityPct != null && humidityPct >= HIGH_HUMIDITY_THRESHOLD_PCT) flags += "HIGH_HUMIDITY"
        if (maxTemperatureC != null && maxTemperatureC >= HOT_THRESHOLD_C) flags += "HOT"
        if (windSpeedMs != null && windSpeedMs >= STRONG_WIND_THRESHOLD_MS) flags += "STRONG_WIND"
        return flags
    }

    private fun parseObservedAt(ncst: List<KmaItem>): LocalDateTime {
        val item = ncst.firstOrNull { it.baseDate != null && it.baseTime != null }
            ?: throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        return try {
            LocalDateTime.parse("${item.baseDate}${item.baseTime}", OBSERVED_AT_FORMAT)
        } catch (exception: Exception) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }
    }

    private fun currentKmaTime(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), KMA_TIME_ZONE)

    private fun resolveSkyCondition(fcst: List<KmaItem>): String {
        // 현재 시각에 가장 가까운(가장 이른) 예보 시각의 SKY/PTY를 사용한다.
        val targetTime = fcst
            .filter { it.category == "SKY" || it.category == "PTY" }
            .minByOrNull { "${it.fcstDate.orEmpty()}${it.fcstTime.orEmpty()}" }
            ?.let { it.fcstDate to it.fcstTime }
            ?: return "정보없음"

        val sky = fcst.firstOrNull {
            it.category == "SKY" && it.fcstDate == targetTime.first && it.fcstTime == targetTime.second
        }?.fcstValue
        val pty = fcst.firstOrNull {
            it.category == "PTY" && it.fcstDate == targetTime.first && it.fcstTime == targetTime.second
        }?.fcstValue

        return skyConditionOf(sky, pty)
    }

    private data class KmaResponse(val response: KmaResponseBody? = null)

    private data class KmaResponseBody(
        val header: KmaHeader? = null,
        val body: KmaBody? = null
    )

    private data class KmaHeader(
        val resultCode: String? = null,
        val resultMsg: String? = null
    )

    private data class KmaBody(val items: KmaItems? = null)

    private data class KmaItems(val item: List<KmaItem> = emptyList())

    private data class KmaItem(
        @JsonProperty("baseDate") val baseDate: String? = null,
        @JsonProperty("baseTime") val baseTime: String? = null,
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("obsrValue") val obsrValue: String? = null,
        @JsonProperty("fcstDate") val fcstDate: String? = null,
        @JsonProperty("fcstTime") val fcstTime: String? = null,
        @JsonProperty("fcstValue") val fcstValue: String? = null
    )

    companion object {
        private const val DEFAULT_NUM_OF_ROWS = 1000
        private const val VILAGE_FCST_NUM_OF_ROWS = 2000
        private const val KMA_SHORT_TERM_SOURCE = "KMA_SHORT_TERM"
        private val KMA_TIME_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val OBSERVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        private val FORECAST_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
        private val EXACT_RAINFALL_PATTERN = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)\s*(?:mm)?\s*$""")
        private val HEAVY_RAIN_THRESHOLD_MM = BigDecimal("20")
        private val HIGH_HUMIDITY_THRESHOLD_PCT = BigDecimal("85")
        private val HOT_THRESHOLD_C = BigDecimal("30")
        private val STRONG_WIND_THRESHOLD_MS = BigDecimal("8")

        private fun createRequestFactory(
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int
        ): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMillis)
                setReadTimeout(readTimeoutMillis)
            }

        // 초단기예보 SKY/PTY 코드 -> 한글 하늘상태. PTY(강수형태)가 우선한다.
        internal fun skyConditionOf(sky: String?, pty: String?): String =
            when (pty) {
                "1" -> "비"
                "2" -> "비/눈"
                "3" -> "눈"
                "4" -> "소나기"
                "5" -> "빗방울"
                "6" -> "빗방울눈날림"
                "7" -> "눈날림"
                else -> when (sky) {
                    "1" -> "맑음"
                    "3" -> "구름많음"
                    "4" -> "흐림"
                    else -> "정보없음"
                }
            }

        private fun parseForecastDate(value: String): LocalDate? =
            try {
                LocalDate.parse(value, FORECAST_DATE_FORMAT)
            } catch (exception: Exception) {
                null
            }

        private fun List<KmaItem>.maxIntValue(category: String): Int? =
            filter { it.category == category }
                .mapNotNull { it.fcstValue?.toIntOrNull() }
                .maxOrNull()

        private fun List<KmaItem>.maxDecimalValue(category: String): BigDecimal? =
            filter { it.category == category }
                .mapNotNull { it.fcstValue?.toBigDecimalOrNull() }
                .maxOrNull()

        private fun String.toExactRainfallMmOrNull(): BigDecimal? {
            val exactValue = EXACT_RAINFALL_PATTERN.matchEntire(this)?.groupValues?.get(1)
                ?: return null
            return exactValue.toBigDecimalOrNull()
        }
    }
}
