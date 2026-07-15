package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.MidTermForecastProvider
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
import java.time.temporal.ChronoUnit

/**
 * 기상청(KMA) 중기예보(MidFcstInfoService의 getMidLandFcst+getMidTa)로 "오늘+4일"(그글피) 하루치
 * 예보를 조회하는 어댑터.
 * - 단기예보(getVilageFcst)는 발표시각에 따라 오늘+3일(글피)까지만 줄 때가 있는데, 그 경우의
 *   5번째 날(오늘+4일)을 이 어댑터로 백필한다 — 그래서 이 날짜 하나만 지원한다.
 * - 육상예보(landRegId)와 기온(taRegId)의 지역코드 체계가 서로 달라 [NearestMidFcstStationResolver]가
 *   한 번의 최근접 탐색으로 둘 다 구한다.
 * - `wf` 필드는 "흐리고 비" 같은 자유 문장이라 [normalizeSkyCondition]으로 단기예보와 같은
 *   어휘(맑음/구름많음/흐림/비/눈)로 정규화한다.
 */
@Component
class KmaMidFcstWeatherProvider internal constructor(
    private val restClient: RestClient,
    private val baseUrl: String,
    private val serviceKey: String
) : MidTermForecastProvider {

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: WeatherMidFcstProperties
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

    override fun fetchDayForecast(latitude: Double, longitude: Double, date: LocalDate): DailyForecast? {
        val dayOffset = ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()
        if (dayOffset != SUPPORTED_DAY_OFFSET) return null

        val station = NearestMidFcstStationResolver.resolve(latitude, longitude)
        val now = LocalDateTime.now()
        val tmFc = KmaBaseTimeResolver.resolveMidFcst(now)
        val fieldIndex = midFcstFieldIndex(now, date)

        val landItem = requestMidLandFcst(station.landRegId, tmFc) ?: return null
        val taItem = requestMidTa(station.taRegId, tmFc) ?: return null

        val minTemperature = taItem.taMinOf(fieldIndex) ?: return null
        val maxTemperature = taItem.taMaxOf(fieldIndex) ?: return null
        val skyCondition = normalizeSkyCondition(landItem.wfOf(fieldIndex)) ?: return null

        return DailyForecast(
            date = date,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature,
            skyCondition = skyCondition
        )
    }

    private fun requestMidLandFcst(regId: String, tmFc: String): MidLandItem? {
        val uri = URI.create(
            "$baseUrl/getMidLandFcst" +
                "?serviceKey=$serviceKey" +
                "&regId=$regId&tmFc=$tmFc" +
                "&dataType=JSON&pageNo=1&numOfRows=10"
        )
        val response = try {
            restClient.get().uri(uri).retrieve().body(MidLandResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        val header = response?.response?.header
        if (header?.resultCode == "03") return null
        if (header?.resultCode != "00") throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)

        return response.response.body?.items?.item?.firstOrNull()
    }

    private fun requestMidTa(regId: String, tmFc: String): MidTaItem? {
        val uri = URI.create(
            "$baseUrl/getMidTa" +
                "?serviceKey=$serviceKey" +
                "&regId=$regId&tmFc=$tmFc" +
                "&dataType=JSON&pageNo=1&numOfRows=10"
        )
        val response = try {
            restClient.get().uri(uri).retrieve().body(MidTaResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        val header = response?.response?.header
        if (header?.resultCode == "03") return null
        if (header?.resultCode != "00") throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)

        return response.response.body?.items?.item?.firstOrNull()
    }

    private data class MidLandResponse(val response: MidLandResponseBody? = null)
    private data class MidLandResponseBody(val header: MidFcstHeader? = null, val body: MidLandBody? = null)
    private data class MidLandBody(val items: MidLandItems? = null)
    private data class MidLandItems(val item: List<MidLandItem> = emptyList())
    private data class MidLandItem(
        @JsonProperty("wf4Am") val wf4Am: String? = null,
        @JsonProperty("wf4Pm") val wf4Pm: String? = null,
        @JsonProperty("wf5Am") val wf5Am: String? = null,
        @JsonProperty("wf5Pm") val wf5Pm: String? = null
    ) {
        // 하루 단위 대표값이라 오후(PM)를 우선하고 없으면 오전(AM)으로 폴백한다.
        fun wfOf(index: Int): String? = when (index) {
            4 -> wf4Pm ?: wf4Am
            5 -> wf5Pm ?: wf5Am
            else -> null
        }
    }

    private data class MidTaResponse(val response: MidTaResponseBody? = null)
    private data class MidTaResponseBody(val header: MidFcstHeader? = null, val body: MidTaBody? = null)
    private data class MidTaBody(val items: MidTaItems? = null)
    private data class MidTaItems(val item: List<MidTaItem> = emptyList())
    private data class MidTaItem(
        @JsonProperty("taMin4") val taMin4: Int? = null,
        @JsonProperty("taMax4") val taMax4: Int? = null,
        @JsonProperty("taMin5") val taMin5: Int? = null,
        @JsonProperty("taMax5") val taMax5: Int? = null
    ) {
        fun taMinOf(index: Int): Int? = when (index) { 4 -> taMin4; 5 -> taMin5; else -> null }
        fun taMaxOf(index: Int): Int? = when (index) { 4 -> taMax4; 5 -> taMax5; else -> null }
    }

    private data class MidFcstHeader(val resultCode: String? = null, val resultMsg: String? = null)

    companion object {
        private const val SUPPORTED_DAY_OFFSET = 4
        private val TM_FC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

        /**
         * 중기예보 wfN/taMinN/taMaxN 필드의 N(발표일 기준 며칠 후)을 구한다(순수 함수).
         * 오늘+4일 대상일 때 발표일이 오늘이면 4, 06:20 이전이라 발표일이 어제면 5가 된다.
         */
        internal fun midFcstFieldIndex(now: LocalDateTime, date: LocalDate): Int {
            val tmFc = KmaBaseTimeResolver.resolveMidFcst(now)
            val announcementDate = LocalDate.parse(tmFc.take(8), TM_FC_DATE_FORMAT)
            return ChronoUnit.DAYS.between(announcementDate, date).toInt()
        }

        // 단기예보(getVilageFcst)의 SKY/PTY 코드값과 어휘(맑음/구름많음/흐림/비/눈)를 맞춘다.
        // 중기예보 wf는 "흐리고 비" 같은 자유 문장이라 키워드 포함 여부로 근사한다. 강수 표현이
        // 있으면 하늘 상태 표현보다 우선한다(단기예보의 PTY 우선 규칙과 동일).
        internal fun normalizeSkyCondition(wf: String?): String? {
            if (wf == null) return null
            return when {
                wf.contains("비") -> "비"
                wf.contains("눈") -> "눈"
                wf.contains("흐") -> "흐림"
                wf.contains("구름많") -> "구름많음"
                wf.contains("맑") -> "맑음"
                else -> "정보없음"
            }
        }

        private fun createRequestFactory(
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int
        ): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMillis)
                setReadTimeout(readTimeoutMillis)
            }
    }
}
