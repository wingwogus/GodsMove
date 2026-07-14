package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.io.IOException
import java.time.LocalDate

class KmaAsosWeatherProviderTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var provider: KmaAsosWeatherProvider

    @BeforeEach
    fun setUp() {
        // 이 어댑터는 항상 하루치(startDt==endDt)만 조회하므로 결과가 1건이면 item이 배열이 아닌
        // 단일 객체로 내려온다(data.go.kr 공통 이슈). ACCEPT_SINGLE_VALUE_AS_ARRAY로 흡수하되,
        // 이 설정은 이 어댑터 전용 RestClient에만 적용하고 전역/다른 어댑터에는 영향을 주지 않는다.
        val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToEnable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build()
        val restClientBuilder = RestClient.builder()
            .messageConverters { converters ->
                converters.removeIf { it is MappingJackson2HttpMessageConverter }
                converters.add(0, MappingJackson2HttpMessageConverter(objectMapper))
            }
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        provider = KmaAsosWeatherProvider(restClientBuilder.build(), BASE_URL, SERVICE_KEY)
    }

    @Test
    fun `단일 객체 JSON 응답(item이 배열이 아님)을 정상 파싱한다`() {
        expectRequest(withJson(SINGLE_OBJECT_BODY))

        val summary = provider.fetchDailySummary(37.5665, 126.9780, LocalDate.of(2026, 7, 1))

        assertThat(summary).isNotNull
        assertThat(summary!!.date).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(summary.minTemperature).isEqualTo(20) // 20.1 반올림
        assertThat(summary.maxTemperature).isEqualTo(29) // 29.4 반올림
        assertThat(summary.skyCondition).isEqualTo("맑음") // avgTca=1.5, sumRn=0.0
        server.verify()
    }

    @Test
    fun `여러 건짜리 배열 JSON 응답도 정상 파싱한다`() {
        expectRequest(withJson(ARRAY_BODY))

        val summary = provider.fetchDailySummary(37.5665, 126.9780, LocalDate.of(2026, 7, 1))

        assertThat(summary).isNotNull
        assertThat(summary!!.minTemperature).isEqualTo(20)
        assertThat(summary.maxTemperature).isEqualTo(29)
        server.verify()
    }

    @Test
    fun `resultCode가 03(NODATA_ERROR)이면 null을 반환한다`() {
        expectRequest(withJson(NODATA_ERROR_BODY))

        val summary = provider.fetchDailySummary(37.5665, 126.9780, LocalDate.of(2026, 7, 1))

        assertThat(summary).isNull()
        server.verify()
    }

    @Test
    fun `resultCode가 00이고 items가 비어있으면 null을 반환한다`() {
        expectRequest(withJson(EMPTY_ITEMS_BODY))

        val summary = provider.fetchDailySummary(37.5665, 126.9780, LocalDate.of(2026, 7, 1))

        assertThat(summary).isNull()
        server.verify()
    }

    @Test
    fun `그 외 resultCode는 제공자 불가 예외를 던진다`() {
        expectRequest(withJson(OTHER_ERROR_BODY))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchDailySummary(37.5665, 126.9780, LocalDate.of(2026, 7, 1))
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `전송 예외는 제공자 불가 예외로 변환된다`() {
        expectRequest(withException(IOException("connection reset")))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchDailySummary(37.5665, 126.9780, LocalDate.of(2026, 7, 1))
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `선택 필드(avgTca)가 JSON 키 자체에서 빠져 있어도 예외 없이 파싱된다`() {
        expectRequest(withJson(NO_AVG_TCA_BODY))

        val summary = provider.fetchDailySummary(37.5665, 126.9780, LocalDate.of(2026, 7, 1))

        assertThat(summary).isNotNull
        assertThat(summary!!.minTemperature).isEqualTo(20)
        assertThat(summary.maxTemperature).isEqualTo(29)
        assertThat(summary.skyCondition).isEqualTo("정보없음") // avgTca 없음, sumRn=0.0
        server.verify()
    }

    private fun expectRequest(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getWthrDataList"))).andRespond(responder)
    }

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    companion object {
        private const val BASE_URL = "https://weather.example.test/AsosDalyInfoService"
        private const val SERVICE_KEY = "test-service-key"

        private val SINGLE_OBJECT_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":
               {"stnId":"108","stnNm":"서울","tm":"2026-07-01","minTa":"20.1","maxTa":"29.4","avgRhm":"55.0","avgWs":"2.1","sumRn":"0.0","avgTca":"1.5"}
             },"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()

        private val ARRAY_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"stnId":"108","stnNm":"서울","tm":"2026-07-01","minTa":"20.1","maxTa":"29.4","avgRhm":"55.0","avgWs":"2.1","sumRn":"0.0","avgTca":"1.5"},
               {"stnId":"108","stnNm":"서울","tm":"2026-07-02","minTa":"18.0","maxTa":"27.0","avgRhm":"60.0","avgWs":"1.9","sumRn":"5.0","avgTca":"8.0"}
             ]},"pageNo":1,"numOfRows":10,"totalCount":2}}}
        """.trimIndent()

        private val NODATA_ERROR_BODY = """
            {"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},
             "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":10,"totalCount":0}}}
        """.trimIndent()

        private val EMPTY_ITEMS_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":10,"totalCount":0}}}
        """.trimIndent()

        private val OTHER_ERROR_BODY = """
            {"response":{"header":{"resultCode":"04","resultMsg":"HTTP_ERROR"},
             "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":10,"totalCount":0}}}
        """.trimIndent()

        private val NO_AVG_TCA_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":
               {"stnId":"108","stnNm":"서울","tm":"2026-07-01","minTa":"20.1","maxTa":"29.4","avgRhm":"55.0","avgWs":"2.1","sumRn":"0.0"}
             },"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()
    }
}
