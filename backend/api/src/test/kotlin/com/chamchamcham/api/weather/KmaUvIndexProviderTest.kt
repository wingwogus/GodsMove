package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.io.IOException

class KmaUvIndexProviderTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var provider: KmaUvIndexProvider

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        provider = KmaUvIndexProvider(restClientBuilder.build(), BASE_URL, SERVICE_KEY)
    }

    @Test
    fun `h0가 채워져 있으면 Int로 파싱한다`() {
        expectRequest(withJson(H0_PRESENT_BODY))

        val uvIndex = provider.fetchUvIndex(AREA_NO)

        assertThat(uvIndex).isEqualTo(7)
        server.verify()
    }

    @Test
    fun `h0가 빈 문자열이면 null을 반환한다`() {
        expectRequest(withJson(H0_BLANK_BODY))

        val uvIndex = provider.fetchUvIndex(AREA_NO)

        assertThat(uvIndex).isNull()
        server.verify()
    }

    @Test
    fun `resultCode가 03(NODATA_ERROR)이면 null을 반환한다`() {
        expectRequest(withJson(NODATA_ERROR_BODY))

        val uvIndex = provider.fetchUvIndex(AREA_NO)

        assertThat(uvIndex).isNull()
        server.verify()
    }

    @Test
    fun `그 외 resultCode는 제공자 불가 예외를 던진다`() {
        expectRequest(withJson(OTHER_ERROR_BODY))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchUvIndex(AREA_NO)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `전송 예외는 제공자 불가 예외로 변환된다`() {
        expectRequest(withException(IOException("connection reset")))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchUvIndex(AREA_NO)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    private fun expectRequest(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getUVIdxV5"))).andRespond(responder)
    }

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    companion object {
        private const val BASE_URL = "https://weather.example.test/LivingWthrIdxServiceV5"
        private const val SERVICE_KEY = "test-service-key"
        private const val AREA_NO = "1100000000"

        private val H0_PRESENT_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"code":"A07_2","areaNo":"1100000000","date":"2026071506","h0":"7","h3":"7","h6":"8"}
             ]},"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()

        private val H0_BLANK_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"code":"A07_2","areaNo":"1100000000","date":"2026071506","h0":"","h3":"7","h6":"8"}
             ]},"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()

        private val NODATA_ERROR_BODY = """
            {"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},
             "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":10,"totalCount":0}}}
        """.trimIndent()

        private val OTHER_ERROR_BODY = """
            {"response":{"header":{"resultCode":"04","resultMsg":"HTTP_ERROR"},
             "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":10,"totalCount":0}}}
        """.trimIndent()
    }
}
