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
import java.time.LocalDate
import java.time.LocalDateTime

class KmaMidFcstWeatherProviderTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var provider: KmaMidFcstWeatherProvider

    private val seoulLatitude = 37.5665
    private val seoulLongitude = 126.9780
    private val today4 = LocalDate.now().plusDays(4)

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        provider = KmaMidFcstWeatherProvider(restClientBuilder.build(), BASE_URL, SERVICE_KEY)
    }

    @Test
    fun `요청 날짜가 오늘+4일이 아니면 외부 호출 없이 null을 반환한다`() {
        val result = provider.fetchDayForecast(seoulLatitude, seoulLongitude, LocalDate.now().plusDays(2))

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `정상 응답이면 오늘+4일 예보를 반환하고 wf 자유 문장을 정규화한다`() {
        expectLandFcst(withJson(LAND_BODY_RAIN))
        expectTa(withJson(TA_BODY))

        val result = provider.fetchDayForecast(seoulLatitude, seoulLongitude, today4)

        assertThat(result).isNotNull
        assertThat(result!!.date).isEqualTo(today4)
        assertThat(result.minTemperature).isEqualTo(24)
        assertThat(result.maxTemperature).isEqualTo(28)
        assertThat(result.skyCondition).isEqualTo("비") // "흐리고 비" -> 비(강수 우선)
        server.verify()
    }

    @Test
    fun `육상예보 resultCode가 03이면 null을 반환하고 기온은 호출하지 않는다`() {
        expectLandFcst(withJson(NODATA_ERROR_BODY))

        val result = provider.fetchDayForecast(seoulLatitude, seoulLongitude, today4)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `기온 resultCode가 03이면 null을 반환한다`() {
        expectLandFcst(withJson(LAND_BODY_RAIN))
        expectTa(withJson(NODATA_ERROR_BODY))

        val result = provider.fetchDayForecast(seoulLatitude, seoulLongitude, today4)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `그 외 resultCode는 제공자 불가 예외를 던진다`() {
        expectLandFcst(withJson(OTHER_ERROR_BODY))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchDayForecast(seoulLatitude, seoulLongitude, today4)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `전송 예외는 제공자 불가 예외로 변환된다`() {
        expectLandFcst(withException(IOException("connection reset")))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchDayForecast(seoulLatitude, seoulLongitude, today4)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `강수 표현이 없는 wf는 하늘상태 어휘로 정규화된다`() {
        expectLandFcst(withJson(LAND_BODY_CLOUDY))
        expectTa(withJson(TA_BODY))

        val result = provider.fetchDayForecast(seoulLatitude, seoulLongitude, today4)

        assertThat(result!!.skyCondition).isEqualTo("구름많음")
    }

    @Test
    fun `강수 없는 흐림은 흐림으로 정규화된다`() {
        assertThat(KmaMidFcstWeatherProvider.normalizeSkyCondition("흐림")).isEqualTo("흐림")
    }

    @Test
    fun `흐리고 비는 강수 우선으로 비로 정규화된다`() {
        assertThat(KmaMidFcstWeatherProvider.normalizeSkyCondition("흐리고 비")).isEqualTo("비")
    }

    @Test
    fun `필드 인덱스는 발표일이 오늘이면 4, 어제면 5를 반환한다`() {
        val target = LocalDate.of(2026, 7, 19)
        assertThat(KmaMidFcstWeatherProvider.midFcstFieldIndex(LocalDateTime.of(2026, 7, 15, 10, 0), target))
            .isEqualTo(4)
        assertThat(KmaMidFcstWeatherProvider.midFcstFieldIndex(LocalDateTime.of(2026, 7, 15, 5, 0), target))
            .isEqualTo(5)
    }

    private fun expectLandFcst(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getMidLandFcst"))).andRespond(responder)
    }

    private fun expectTa(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getMidTa"))).andRespond(responder)
    }

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    companion object {
        private const val BASE_URL = "https://weather.example.test/MidFcstInfoService"
        private const val SERVICE_KEY = "test-service-key"

        // 실행 시각(낮=필드4, 새벽=필드5)에 무관하게 통과하도록 wf4/wf5, ta4/ta5를 같은 값으로 채운다.
        private val LAND_BODY_RAIN = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"regId":"11B10000","wf4Am":"흐리고 비","wf4Pm":"흐리고 비","wf5Am":"흐리고 비","wf5Pm":"흐리고 비"}
             ]},"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()

        private val LAND_BODY_CLOUDY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"regId":"11B10000","wf4Am":"구름많음","wf4Pm":"구름많음","wf5Am":"구름많음","wf5Pm":"구름많음"}
             ]},"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()

        private val TA_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"regId":"11B10101","taMin4":24,"taMax4":28,"taMin5":24,"taMax5":28}
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
