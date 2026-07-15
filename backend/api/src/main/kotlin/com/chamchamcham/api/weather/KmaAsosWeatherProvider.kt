package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.DailyWeatherSummary
import com.chamchamcham.application.weather.HistoricalWeatherProvider
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 기상청(KMA) 종관기상관측(ASOS) 일자료(AsosDalyInfoService/getWthrDataList)로 과거 특정 날짜의
 * 일별 요약(최저/최고기온, 하늘상태)을 조회하는 어댑터.
 * - 관측지점은 위경도로부터 [NearestAsosStationResolver]가 최근접 지점을 찾는다.
 * - 하늘상태 문자열은 [AsosSkyConditionMapper]가 평균전운량(avgTca)/일강수량(sumRn)으로 근사한다.
 * - data.go.kr 공통 이슈: 결과가 1건이면 `item`이 배열이 아닌 단일 객체로 내려온다. 이 어댑터
 *   전용 RestClient에만 `ACCEPT_SINGLE_VALUE_AS_ARRAY`를 켜서 흡수하고, 다른 어댑터(KmaWeatherProvider
 *   등)의 파싱에는 영향을 주지 않는다.
 */
@Component
class KmaAsosWeatherProvider internal constructor(
    private val restClient: RestClient,
    private val baseUrl: String,
    private val serviceKey: String
) : HistoricalWeatherProvider {

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: WeatherAsosProperties
    ) : this(
        restClientBuilder
            .requestFactory(
                createRequestFactory(
                    properties.connectTimeoutMillis,
                    properties.readTimeoutMillis
                )
            )
            .messageConverters { converters ->
                converters.removeIf { it is MappingJackson2HttpMessageConverter }
                converters.add(0, MappingJackson2HttpMessageConverter(createObjectMapper()))
            }
            .build(),
        properties.baseUrl,
        properties.serviceKey
    )

    override fun fetchDailySummary(latitude: Double, longitude: Double, date: LocalDate): DailyWeatherSummary? {
        val station = NearestAsosStationResolver.resolve(latitude, longitude)
        val item = requestItem(station.id, date) ?: return null

        val minTemperature = item.minTa?.toDoubleOrNull()?.roundToInt() ?: return null
        val maxTemperature = item.maxTa?.toDoubleOrNull()?.roundToInt() ?: return null

        val skyCondition = AsosSkyConditionMapper.of(
            avgTca = item.avgTca?.toDoubleOrNull(),
            sumRn = item.sumRn?.toDoubleOrNull()
        )

        return DailyWeatherSummary(
            date = date,
            skyCondition = skyCondition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature
        )
    }

    private fun requestItem(stationId: String, date: LocalDate): AsosItem? {
        val dateStr = date.format(DATE_FORMAT)
        // serviceKey는 공공데이터포털 인코딩 키가 그대로 전달되도록 pre-encoded URI로 요청(이중 인코딩 방지).
        val uri = URI.create(
            "$baseUrl/getWthrDataList" +
                "?serviceKey=$serviceKey" +
                "&dataCd=ASOS&dateCd=DAY" +
                "&startDt=$dateStr&endDt=$dateStr" +
                "&stnIds=$stationId" +
                "&pageNo=1&numOfRows=10&dataType=JSON"
        )

        val response = try {
            restClient.get()
                .uri(uri)
                .retrieve()
                .body(AsosResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        val header = response?.response?.header
        if (header?.resultCode == "03") return null
        if (header?.resultCode != "00") throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)

        val items = response.response.body?.items?.item ?: emptyList()
        return items.firstOrNull()
    }

    private data class AsosResponse(val response: AsosResponseBody? = null)

    private data class AsosResponseBody(
        val header: AsosHeader? = null,
        val body: AsosBody? = null
    )

    private data class AsosHeader(
        val resultCode: String? = null,
        val resultMsg: String? = null
    )

    private data class AsosBody(val items: AsosItems? = null)

    private data class AsosItems(val item: List<AsosItem> = emptyList())

    private data class AsosItem(
        @JsonProperty("stnId") val stnId: String? = null,
        @JsonProperty("tm") val tm: String? = null,
        @JsonProperty("minTa") val minTa: String? = null,
        @JsonProperty("maxTa") val maxTa: String? = null,
        @JsonProperty("avgRhm") val avgRhm: String? = null,
        @JsonProperty("avgWs") val avgWs: String? = null,
        @JsonProperty("sumRn") val sumRn: String? = null,
        @JsonProperty("avgTca") val avgTca: String? = null
    )

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

        private fun createRequestFactory(
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int
        ): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMillis)
                setReadTimeout(readTimeoutMillis)
            }

        private fun createObjectMapper(): ObjectMapper =
            Jackson2ObjectMapperBuilder.json()
                .featuresToEnable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .build()
    }
}
