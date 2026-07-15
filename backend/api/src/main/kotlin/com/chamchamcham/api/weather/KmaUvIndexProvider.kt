package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.UvIndexProvider
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 기상청(KMA) 생활기상지수(LivingWthrIdxServiceV5/getUVIdxV5)로 현재 자외선지수(h0)를
 * 조회하는 어댑터.
 * - 5일 패널이 아닌, 현재 시각(3시간 단위로 내림한) 기준 단일 값만 사용한다.
 * - h0가 없거나(키 부재/null) 빈 문자열이면 데이터없음으로 간주해 null을 반환한다.
 */
@Component
class KmaUvIndexProvider internal constructor(
    private val restClient: RestClient,
    private val baseUrl: String,
    private val serviceKey: String
) : UvIndexProvider {

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: WeatherUvProperties
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

    override fun fetchUvIndex(areaNo: String): Int? {
        val time = KmaBaseTimeResolver.resolveUvIdx(LocalDateTime.now()).format(TIME_FORMAT)

        // serviceKey는 공공데이터포털 인코딩 키가 그대로 전달되도록 pre-encoded URI로 요청(이중 인코딩 방지).
        val uri = URI.create(
            "$baseUrl/getUVIdxV5" +
                "?serviceKey=$serviceKey" +
                "&areaNo=$areaNo&time=$time" +
                "&dataType=JSON&pageNo=1&numOfRows=10"
        )

        val response = try {
            restClient.get()
                .uri(uri)
                .retrieve()
                .body(UvIdxResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        val header = response?.response?.header
        if (header?.resultCode == "03") return null
        if (header?.resultCode != "00") throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)

        val item = response.response.body?.items?.item?.firstOrNull() ?: return null
        return item.h0?.toIntOrNull()
    }

    private data class UvIdxResponse(val response: UvIdxResponseBody? = null)

    private data class UvIdxResponseBody(
        val header: UvIdxHeader? = null,
        val body: UvIdxBody? = null
    )

    private data class UvIdxHeader(
        val resultCode: String? = null,
        val resultMsg: String? = null
    )

    private data class UvIdxBody(val items: UvIdxItems? = null)

    private data class UvIdxItems(val item: List<UvIdxItem> = emptyList())

    private data class UvIdxItem(
        @JsonProperty("areaNo") val areaNo: String? = null,
        @JsonProperty("date") val date: String? = null,
        @JsonProperty("h0") val h0: String? = null
    )

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")

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
