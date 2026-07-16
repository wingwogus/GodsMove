package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI

/**
 * 기상청 API 4벌(단기예보/중기예보/ASOS/생활기상지수) 호출의 단일 통로.
 * 어댑터마다 흩어져 있던 RestClient 생성, resultCode 분기, 예외 변환을 여기 하나로 모은다.
 *
 * 응답 파싱을 RestClient의 메시지 컨버터가 아니라 여기서 직접 하는 이유는 `itemType`이
 * 호출마다 달라서(단기예보 item, ASOS item, ...) 제네릭 타입을 런타임에 조립해야 하기 때문이다.
 * 같은 이유로 `ACCEPT_SINGLE_VALUE_AS_ARRAY`(item이 1건일 때 배열 대신 단일 객체로 오는
 * data.go.kr 공통 이슈, ASOS에서 실측됨)도 이 전용 ObjectMapper 하나에만 켠다.
 */
@Component
class KmaApiClient internal constructor(
    private val restClient: RestClient,
    private val serviceKey: String
) {
    private val logger = KotlinLogging.logger {}

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: KmaProperties
    ) : this(
        restClientBuilder
            .requestFactory(
                createRequestFactory(
                    properties.connectTimeoutMillis,
                    properties.readTimeoutMillis
                )
            )
            .build(),
        properties.serviceKey
    )

    /**
     * 기상청 응답은 불변이다 — 같은 격자·같은 발표시각이면 영원히 같은 값이다. `params`에 이미
     * base_date/base_time(또는 tmFc, time)과 격자가 들어 있어서 캐시 키가 발표시각을 저절로
     * 포함하고, 그래서 다음 발표는 갱신이 아니라 새 키가 된다. 무효화 로직이 필요 없는 이유다.
     *
     * 빈 결과는 캐싱하지 않는다. 03/99는 대부분 진짜 데이터 부재지만(예: ASOS의 오늘 조회),
     * 일시적 장애도 같은 코드로 올 수 있어 그걸 TTL 내내 고착시키면 안 된다.
     */
    @Cacheable(cacheNames = ["kma"], key = "#operation + '|' + #params", unless = "#result.isEmpty()")
    fun <T> getItems(
        baseUrl: String,
        operation: String,
        params: Map<String, String>,
        itemType: Class<T>
    ): List<T> {
        val uri = buildUri(baseUrl, operation, params)

        val rawBody = try {
            restClient.get().uri(uri).retrieve().body(String::class.java)
        } catch (exception: RestClientException) {
            logger.warn { "기상청 API 호출 실패: operation=$operation" }
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        // data.go.kr은 JSON을 요청해도 오류 상황에서 XML을 돌려줄 때가 있어, 여기서도 실패할 수 있다.
        val envelope: KmaEnvelope<T> = try {
            val javaType = OBJECT_MAPPER.typeFactory.constructParametricType(KmaEnvelope::class.java, itemType)
            OBJECT_MAPPER.readValue(rawBody.orEmpty(), javaType)
        } catch (exception: Exception) {
            logger.warn { "기상청 API 응답 파싱 실패: operation=$operation" }
            throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
        }

        val header = envelope.response?.header
        return when (header?.resultCode) {
            SUCCESS_RESULT_CODE -> envelope.response.body?.items?.item ?: emptyList()
            NO_DATA_RESULT_CODE -> {
                logger.debug { "기상청 API 조회결과 없음(03): operation=$operation" }
                emptyList()
            }
            // 실측상 99는 "검색결과가 없습니다"/"전날 자료까지 제공됩니다" 등 데이터 부재 의미로 온다.
            NO_DATA_ALT_RESULT_CODE -> {
                logger.warn { "기상청 API 조회결과 없음(99): operation=$operation resultMsg=${header.resultMsg}" }
                emptyList()
            }
            else -> {
                logger.warn {
                    "기상청 API 오류: operation=$operation resultCode=${header?.resultCode} resultMsg=${header?.resultMsg}"
                }
                throw BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
            }
        }
    }

    private fun buildUri(baseUrl: String, operation: String, params: Map<String, String>): URI {
        // serviceKey는 공공데이터포털 인코딩 키가 그대로 전달되도록 pre-encoded URI로 요청한다.
        // RestClient의 uriBuilder를 쓰면 이미 인코딩된 값을 다시 인코딩해(이중 인코딩) 키가 깨진다.
        val merged = linkedMapOf(
            "dataType" to "JSON",
            "pageNo" to "1"
        )
        merged.putAll(params)
        val query = merged.entries.joinToString("&") { (key, value) -> "$key=$value" }
        return URI.create("$baseUrl/$operation?serviceKey=$serviceKey&$query")
    }

    private data class KmaEnvelope<T>(val response: KmaResponseBody<T>? = null)
    private data class KmaResponseBody<T>(val header: KmaHeader? = null, val body: KmaBody<T>? = null)
    private data class KmaHeader(val resultCode: String? = null, val resultMsg: String? = null)
    private data class KmaBody<T>(val items: KmaItems<T>? = null)
    private data class KmaItems<T>(val item: List<T> = emptyList())

    companion object {
        private const val SUCCESS_RESULT_CODE = "00"
        private const val NO_DATA_RESULT_CODE = "03"
        private const val NO_DATA_ALT_RESULT_CODE = "99"

        private val OBJECT_MAPPER: ObjectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToEnable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build()

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
