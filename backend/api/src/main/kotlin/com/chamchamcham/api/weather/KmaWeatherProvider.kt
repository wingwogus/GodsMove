package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.WeatherForecast
import com.chamchamcham.application.weather.WeatherProvider
import com.chamchamcham.application.weather.WeatherSnapshot
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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
    private val serviceKey: String
) : WeatherProvider {

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
        val now = LocalDateTime.now()

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

        val humidity = ncst.firstOrNull { it.category == "REH" }?.obsrValue
            ?.toDoubleOrNull()
            ?.roundToInt()
        val windSpeed = ncst.firstOrNull { it.category == "WSD" }?.obsrValue
            ?.toDoubleOrNull()

        val observedAt = parseObservedAt(ncst)
        val skyCondition = resolveSkyCondition(fcst)
        val feelsLikeTemperature = FeelsLikeTemperatureCalculator.of(
            temperature = temperature,
            humidity = humidity,
            windSpeedMps = windSpeed,
            month = observedAt.monthValue
        )

        return WeatherSnapshot(
            temperature = temperature,
            skyCondition = skyCondition,
            observedAt = observedAt,
            humidity = humidity,
            windSpeed = windSpeed,
            feelsLikeTemperature = feelsLikeTemperature
        )
    }

    override fun fetchForecastPanel(latitude: Double, longitude: Double): WeatherForecast {
        val grid = GeoToGridConverter.convert(latitude, longitude)
        val now = LocalDateTime.now()

        val items = requestItems(
            path = "getVilageFcst",
            base = KmaBaseTimeResolver.resolveVilageFcst(now),
            nx = grid.nx,
            ny = grid.ny
        )

        val dailyForecasts = items
            .mapNotNull { it.fcstDate }
            .distinct()
            .sorted()
            .map { fcstDate -> buildDailyForecast(fcstDate, items) }

        return WeatherForecast(
            precipitationProbability = resolvePrecipitationProbability(items, now),
            dailyForecasts = dailyForecasts
        )
    }

    private fun buildDailyForecast(fcstDate: String, items: List<KmaItem>): DailyForecast {
        val dayItems = items.filter { it.fcstDate == fcstDate }

        val tmn = dayItems.firstOrNull { it.category == "TMN" && it.fcstTime == "0600" }
            ?.fcstValue?.toDoubleOrNull()?.roundToInt()
        val tmx = dayItems.firstOrNull { it.category == "TMX" && it.fcstTime == "1500" }
            ?.fcstValue?.toDoubleOrNull()?.roundToInt()
        val tmpValues = dayItems.filter { it.category == "TMP" }.mapNotNull { it.fcstValue?.toDoubleOrNull() }

        val nearestNoon = dayItems
            .filter { it.category == "SKY" || it.category == "PTY" }
            .minByOrNull { minutesFromTarget(it.fcstTime, targetMinutes = 12 * 60) }
            ?.fcstTime
        // getVilageFcst는 PTY 코드로 {0,1,2,3,4}만 내려주지만(초단기예보의 5/6/7은 여기 없음), skyConditionOf는 상위호환이라 재사용해도 안전하다.
        val skyCondition = nearestNoon?.let { fcstTime ->
            val sky = dayItems.firstOrNull { it.category == "SKY" && it.fcstTime == fcstTime }?.fcstValue
            val pty = dayItems.firstOrNull { it.category == "PTY" && it.fcstTime == fcstTime }?.fcstValue
            skyConditionOf(sky, pty)
        }

        return DailyForecast(
            date = LocalDate.parse(fcstDate, FCST_DATE_FORMAT),
            minTemperature = tmn ?: tmpValues.minOrNull()?.roundToInt(),
            maxTemperature = tmx ?: tmpValues.maxOrNull()?.roundToInt(),
            skyCondition = skyCondition
        )
    }

    private fun resolvePrecipitationProbability(items: List<KmaItem>, now: LocalDateTime): Int? {
        val today = now.format(FCST_DATE_FORMAT)
        val nowMinutes = now.hour * 60 + now.minute
        return items
            .filter { it.category == "POP" && it.fcstDate == today }
            .minByOrNull { minutesFromTarget(it.fcstTime, targetMinutes = nowMinutes) }
            ?.fcstValue?.toDoubleOrNull()?.roundToInt()
    }

    private fun minutesFromTarget(fcstTime: String?, targetMinutes: Int): Int {
        if (fcstTime == null || fcstTime.length != 4) return Int.MAX_VALUE
        val hour = fcstTime.substring(0, 2).toIntOrNull() ?: return Int.MAX_VALUE
        val minute = fcstTime.substring(2, 4).toIntOrNull() ?: return Int.MAX_VALUE
        return abs(hour * 60 + minute - targetMinutes)
    }

    private fun requestItems(path: String, base: KmaBaseDateTime, nx: Int, ny: Int): List<KmaItem> {
        // serviceKey는 공공데이터포털 인코딩 키가 그대로 전달되도록 pre-encoded URI로 요청(이중 인코딩 방지).
        val uri = URI.create(
            "$baseUrl/$path" +
                "?serviceKey=$serviceKey" +
                "&pageNo=1&numOfRows=1000&dataType=JSON" +
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

    private fun parseObservedAt(ncst: List<KmaItem>): LocalDateTime {
        val item = ncst.firstOrNull { it.baseDate != null && it.baseTime != null }
            ?: throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        return try {
            LocalDateTime.parse("${item.baseDate}${item.baseTime}", OBSERVED_AT_FORMAT)
        } catch (exception: Exception) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }
    }

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
        private val OBSERVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        private val FCST_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

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
    }
}
