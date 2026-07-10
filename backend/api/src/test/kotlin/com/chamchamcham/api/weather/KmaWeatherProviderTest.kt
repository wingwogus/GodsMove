package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.allOf
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class KmaWeatherProviderTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var provider: KmaWeatherProvider

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        provider = KmaWeatherProvider(restClientBuilder.build(), BASE_URL, SERVICE_KEY, FIXED_CLOCK)
    }

    @Test
    fun `실황 기온과 예보 하늘상태를 조합해 스냅샷을 만든다`() {
        expectNcst(withJson(NCST_BODY))
        expectFcst(withJson(FCST_CLEAR_BODY))

        val snapshot = provider.fetchCurrentWeather(37.5665, 126.9780)

        assertThat(snapshot.temperature).isEqualTo(14) // 14.2 반올림
        assertThat(snapshot.skyCondition).isEqualTo("맑음") // 가장 이른 예보(1100): SKY=1, PTY=0
        assertThat(snapshot.observedAt).isEqualTo(LocalDateTime.of(2026, 7, 8, 10, 0))
        server.verify()
    }

    @Test
    fun `강수형태(PTY)가 하늘상태(SKY)보다 우선한다`() {
        expectNcst(withJson(NCST_BODY))
        expectFcst(withJson(FCST_RAIN_BODY))

        val snapshot = provider.fetchCurrentWeather(37.5665, 126.9780)

        assertThat(snapshot.skyCondition).isEqualTo("비") // SKY=1 이지만 PTY=1 이므로 비
    }

    @Test
    fun `실황 응답 결과코드가 정상이 아니면 제공자 불가 예외를 던진다`() {
        expectNcst(withJson(NO_DATA_BODY))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchCurrentWeather(37.5665, 126.9780)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `전송 예외는 제공자 불가 예외로 변환된다`() {
        expectNcst(withException(IOException("connection reset")))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchCurrentWeather(37.5665, 126.9780)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `실황에 기온이 없으면 제공자 불가 예외를 던진다`() {
        expectNcst(withJson(NCST_NO_TEMPERATURE_BODY))
        expectFcst(withJson(FCST_CLEAR_BODY))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetchCurrentWeather(37.5665, 126.9780)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `SKY_PTY 코드 매핑을 검증한다`() {
        assertThat(KmaWeatherProvider.skyConditionOf(sky = "1", pty = "0")).isEqualTo("맑음")
        assertThat(KmaWeatherProvider.skyConditionOf(sky = "3", pty = "0")).isEqualTo("구름많음")
        assertThat(KmaWeatherProvider.skyConditionOf(sky = "4", pty = "0")).isEqualTo("흐림")
        assertThat(KmaWeatherProvider.skyConditionOf(sky = "1", pty = "3")).isEqualTo("눈")
        assertThat(KmaWeatherProvider.skyConditionOf(sky = null, pty = null)).isEqualTo("정보없음")
    }

    @Test
    fun `기록 피드백 날씨는 동네예보 일별 값을 집계하고 누락된 날짜를 만들지 않는다`() {
        expectNcst(withJson(NCST_BODY))
        expectFcst(withJson(FCST_CLEAR_BODY))
        expectVilageFcst(withJson(VILAGE_FCST_THREE_DAYS_BODY))

        val result = provider.fetch(37.5665, 126.9780, limitDays = 7)

        assertThat(result.current.temperatureC).isEqualTo(14)
        assertThat(result.current.skyCondition).isEqualTo("맑음")
        assertThat(result.current.observedAt).isEqualTo(LocalDateTime.of(2026, 7, 8, 10, 0))
        assertThat(result.source).isEqualTo("KMA_SHORT_TERM")
        assertThat(result.forecastDays).hasSize(3)
        assertThat(result.forecastDays.map { it.date.toString() })
            .containsExactly("2026-07-11", "2026-07-12", "2026-07-13")

        val firstDay = result.forecastDays.first()
        assertThat(firstDay.rainfallMm).isEqualByComparingTo("22.5")
        assertThat(firstDay.rainProbabilityPct).isEqualTo(80)
        assertThat(firstDay.maxTemperatureC).isEqualByComparingTo("31")
        assertThat(firstDay.minTemperatureC).isEqualByComparingTo("24")
        assertThat(firstDay.humidityPct).isEqualByComparingTo("88")
        assertThat(firstDay.windSpeedMs).isEqualByComparingTo("9.1")
        assertThat(firstDay.riskFlags)
            .containsExactly("RAIN", "HEAVY_RAIN", "HIGH_HUMIDITY", "HOT", "STRONG_WIND")
        assertThat(result.forecastDays[1].riskFlags).containsExactly("RAIN")
        assertThat(result.forecastDays[2].riskFlags).isEmpty()
        server.verify()
    }

    @Test
    fun `기록 피드백 날씨는 limitDays 범위만 허용한다`() {
        assertThatThrownBy { provider.fetch(37.5665, 126.9780, limitDays = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { provider.fetch(37.5665, 126.9780, limitDays = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `동네예보 오류는 제공자 불가 예외로 변환된다`() {
        expectNcst(withJson(NCST_BODY))
        expectFcst(withJson(FCST_CLEAR_BODY))
        expectVilageFcst(withJson(NO_DATA_BODY))

        val exception = assertThrows(BusinessException::class.java) {
            provider.fetch(37.5665, 126.9780, limitDays = 3)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `정성 강수량은 숫자로 만들지 않고 강수확률과 강수형태만 위험 신호에 반영한다`() {
        expectNcst(withJson(NCST_BODY))
        expectFcst(withJson(FCST_CLEAR_BODY))
        expectVilageFcst(withJson(VILAGE_FCST_QUALITATIVE_PCP_BODY))

        val result = provider.fetch(37.5665, 126.9780, limitDays = 1)

        assertThat(result.forecastDays).hasSize(1)
        assertThat(result.forecastDays.first().rainfallMm).isNull()
        assertThat(result.forecastDays.first().rainProbabilityPct).isEqualTo(70)
        assertThat(result.forecastDays.first().riskFlags).containsExactly("RAIN")
    }

    private fun expectNcst(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getUltraSrtNcst"))).andRespond(responder)
    }

    private fun expectFcst(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getUltraSrtFcst"))).andRespond(responder)
    }

    private fun expectVilageFcst(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(
            requestTo(
                allOf(
                    containsString("getVilageFcst"),
                    containsString("numOfRows=2000"),
                    containsString("base_date=20260711"),
                    containsString("base_time=0500")
                )
            )
        ).andRespond(responder)
    }

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    companion object {
        private const val BASE_URL = "https://weather.example.test/VilageFcstInfoService_2.0"
        private const val SERVICE_KEY = "test-service-key"
        // 20:10 UTC is 05:10 KST on the next day. KMA issue times are KST.
        private val FIXED_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-07-10T20:10:00Z"),
            ZoneId.of("UTC")
        )

        private val NCST_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260708","baseTime":"1000","category":"T1H","nx":60,"ny":127,"obsrValue":"14.2"},
               {"baseDate":"20260708","baseTime":"1000","category":"REH","nx":60,"ny":127,"obsrValue":"55"},
               {"baseDate":"20260708","baseTime":"1000","category":"RN1","nx":60,"ny":127,"obsrValue":"0"},
               {"baseDate":"20260708","baseTime":"1000","category":"WSD","nx":60,"ny":127,"obsrValue":"2.1"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":4}}}
        """.trimIndent()

        private val NCST_NO_TEMPERATURE_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260708","baseTime":"1000","category":"REH","nx":60,"ny":127,"obsrValue":"55"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":1}}}
        """.trimIndent()

        private val FCST_CLEAR_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260708","baseTime":"0930","category":"PTY","fcstDate":"20260708","fcstTime":"1200","fcstValue":"1"},
               {"baseDate":"20260708","baseTime":"0930","category":"SKY","fcstDate":"20260708","fcstTime":"1200","fcstValue":"4"},
               {"baseDate":"20260708","baseTime":"0930","category":"PTY","fcstDate":"20260708","fcstTime":"1100","fcstValue":"0"},
               {"baseDate":"20260708","baseTime":"0930","category":"SKY","fcstDate":"20260708","fcstTime":"1100","fcstValue":"1"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":4}}}
        """.trimIndent()

        private val FCST_RAIN_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260708","baseTime":"0930","category":"PTY","fcstDate":"20260708","fcstTime":"1100","fcstValue":"1"},
               {"baseDate":"20260708","baseTime":"0930","category":"SKY","fcstDate":"20260708","fcstTime":"1100","fcstValue":"1"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":2}}}
        """.trimIndent()

        private val NO_DATA_BODY = """
            {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"},
             "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":1000,"totalCount":0}}}
        """.trimIndent()

        private val VILAGE_FCST_THREE_DAYS_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260711","baseTime":"0500","category":"POP","fcstDate":"20260710","fcstTime":"1500","fcstValue":"90"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMP","fcstDate":"20260711","fcstTime":"0900","fcstValue":"25"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMN","fcstDate":"20260711","fcstTime":"0600","fcstValue":"24"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMX","fcstDate":"20260711","fcstTime":"1500","fcstValue":"31"},
               {"baseDate":"20260711","baseTime":"0500","category":"POP","fcstDate":"20260711","fcstTime":"0900","fcstValue":"80"},
               {"baseDate":"20260711","baseTime":"0500","category":"REH","fcstDate":"20260711","fcstTime":"1200","fcstValue":"88"},
               {"baseDate":"20260711","baseTime":"0500","category":"WSD","fcstDate":"20260711","fcstTime":"1200","fcstValue":"9.1"},
               {"baseDate":"20260711","baseTime":"0500","category":"PTY","fcstDate":"20260711","fcstTime":"1200","fcstValue":"1"},
               {"baseDate":"20260711","baseTime":"0500","category":"PCP","fcstDate":"20260711","fcstTime":"1200","fcstValue":"22.5mm"},
               {"baseDate":"20260711","baseTime":"0500","category":"SKY","fcstDate":"20260711","fcstTime":"1200","fcstValue":"4"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMN","fcstDate":"20260712","fcstTime":"0600","fcstValue":"20"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMX","fcstDate":"20260712","fcstTime":"1500","fcstValue":"27"},
               {"baseDate":"20260711","baseTime":"0500","category":"POP","fcstDate":"20260712","fcstTime":"1200","fcstValue":"40"},
               {"baseDate":"20260711","baseTime":"0500","category":"PTY","fcstDate":"20260712","fcstTime":"1200","fcstValue":"1"},
               {"baseDate":"20260711","baseTime":"0500","category":"REH","fcstDate":"20260712","fcstTime":"1200","fcstValue":"70"},
               {"baseDate":"20260711","baseTime":"0500","category":"WSD","fcstDate":"20260712","fcstTime":"1200","fcstValue":"3.5"},
               {"baseDate":"20260711","baseTime":"0500","category":"PCP","fcstDate":"20260712","fcstTime":"1200","fcstValue":"강수없음"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMP","fcstDate":"20260713","fcstTime":"0900","fcstValue":"23"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMN","fcstDate":"20260713","fcstTime":"0600","fcstValue":"21"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMX","fcstDate":"20260713","fcstTime":"1500","fcstValue":"29"},
               {"baseDate":"20260711","baseTime":"0500","category":"POP","fcstDate":"20260713","fcstTime":"1200","fcstValue":"20"},
               {"baseDate":"20260711","baseTime":"0500","category":"PTY","fcstDate":"20260713","fcstTime":"1200","fcstValue":"0"},
               {"baseDate":"20260711","baseTime":"0500","category":"REH","fcstDate":"20260713","fcstTime":"1200","fcstValue":"60"},
               {"baseDate":"20260711","baseTime":"0500","category":"WSD","fcstDate":"20260713","fcstTime":"1200","fcstValue":"2.5"},
               {"baseDate":"20260711","baseTime":"0500","category":"PCP","fcstDate":"20260713","fcstTime":"1200","fcstValue":"0"}
             ]},"pageNo":1,"numOfRows":2000,"totalCount":25}}}
        """.trimIndent()

        private val VILAGE_FCST_QUALITATIVE_PCP_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260711","baseTime":"0500","category":"TMN","fcstDate":"20260711","fcstTime":"0600","fcstValue":"22"},
               {"baseDate":"20260711","baseTime":"0500","category":"TMX","fcstDate":"20260711","fcstTime":"1500","fcstValue":"28"},
               {"baseDate":"20260711","baseTime":"0500","category":"POP","fcstDate":"20260711","fcstTime":"1200","fcstValue":"70"},
               {"baseDate":"20260711","baseTime":"0500","category":"PTY","fcstDate":"20260711","fcstTime":"1200","fcstValue":"0"},
               {"baseDate":"20260711","baseTime":"0500","category":"PCP","fcstDate":"20260711","fcstTime":"1200","fcstValue":"1.0mm 미만"}
             ]},"pageNo":1,"numOfRows":2000,"totalCount":5}}}
        """.trimIndent()
    }
}
