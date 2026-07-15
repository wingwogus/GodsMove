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
import java.time.format.DateTimeFormatter

class KmaWeatherProviderTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var provider: KmaWeatherProvider

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        provider = KmaWeatherProvider(restClientBuilder.build(), BASE_URL, SERVICE_KEY)
    }

    @Test
    fun `실황 기온과 예보 하늘상태를 조합해 스냅샷을 만든다`() {
        expectNcst(withJson(NCST_BODY))
        expectFcst(withJson(FCST_CLEAR_BODY))

        val snapshot = provider.fetchCurrentWeather(37.5665, 126.9780)

        assertThat(snapshot.temperature).isEqualTo(14) // 14.2 반올림
        assertThat(snapshot.skyCondition).isEqualTo("맑음") // 가장 이른 예보(1100): SKY=1, PTY=0
        assertThat(snapshot.observedAt).isEqualTo(LocalDateTime.of(2026, 7, 8, 10, 0))
        assertThat(snapshot.humidity).isEqualTo(55)
        assertThat(snapshot.windSpeed).isEqualTo(2.1)
        // 7월(여름철) 열지수 공식: Ta=14, RH=55 -> Tw≈9.08 -> feelsLike≈14.33 -> 반올림 14
        assertThat(snapshot.feelsLikeTemperature).isEqualTo(14)
        server.verify()
    }

    @Test
    fun `실황에 습도와 풍속이 없어도 스냅샷은 null로 성공한다`() {
        expectNcst(withJson(NCST_NO_HUMIDITY_WIND_BODY))
        expectFcst(withJson(FCST_CLEAR_BODY))

        val snapshot = provider.fetchCurrentWeather(37.5665, 126.9780)

        assertThat(snapshot.temperature).isEqualTo(14)
        assertThat(snapshot.humidity).isNull()
        assertThat(snapshot.windSpeed).isNull()
        // 7월(여름철)인데 습도가 없어 체감온도를 계산할 수 없다.
        assertThat(snapshot.feelsLikeTemperature).isNull()
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
    fun `base_time 02~14 응답은 4개의 예보일을 반환한다`() {
        expectVilageFcst(withJson(VILAGE_FCST_4_DAYS_BODY))

        val forecast = provider.fetchForecastPanel(37.5665, 126.9780)

        assertThat(forecast.dailyForecasts).hasSize(4)
        assertThat(forecast.dailyForecasts.map { it.date }).containsExactly(
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 9),
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 11)
        )
        val today = forecast.dailyForecasts.first()
        assertThat(today.minTemperature).isEqualTo(18) // TMN@0600
        assertThat(today.maxTemperature).isEqualTo(29) // TMX@1500
        assertThat(today.skyCondition).isEqualTo("맑음") // 정오(1200)에 가장 가까운 SKY=1,PTY=0
        server.verify()
    }

    @Test
    fun `base_time 17~23 응답은 5개의 예보일을 반환하며 오늘 TMN_TMX가 없으면 TMP로 대체한다`() {
        expectVilageFcst(withJson(VILAGE_FCST_5_DAYS_NO_TODAY_TMN_TMX_BODY))

        val forecast = provider.fetchForecastPanel(37.5665, 126.9780)

        assertThat(forecast.dailyForecasts).hasSize(5)
        val today = forecast.dailyForecasts.first()
        assertThat(today.minTemperature).isEqualTo(18) // TMN 없음 -> TMP 최솟값
        assertThat(today.maxTemperature).isEqualTo(22) // TMX 없음 -> TMP 최댓값
        val secondDay = forecast.dailyForecasts[1]
        assertThat(secondDay.minTemperature).isEqualTo(17) // TMN@0600 존재
        assertThat(secondDay.maxTemperature).isEqualTo(27) // TMX@1500 존재
        server.verify()
    }

    @Test
    fun `강수확률은 현재 시각에 가장 가까운 POP 값을 사용한다`() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val closeHour = "%02d00".format(now.hour)
        val farHour = "%02d00".format((now.hour + 12) % 24)
        expectVilageFcst(
            withJson(
                vilageFcstBodyWithPop(fcstDate = today, closeTime = closeHour, farTime = farHour)
            )
        )

        val forecast = provider.fetchForecastPanel(37.5665, 126.9780)

        assertThat(forecast.precipitationProbability).isEqualTo(30)
        server.verify()
    }

    private fun expectNcst(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getUltraSrtNcst"))).andRespond(responder)
    }

    private fun expectFcst(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getUltraSrtFcst"))).andRespond(responder)
    }

    private fun expectVilageFcst(responder: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo(containsString("getVilageFcst"))).andRespond(responder)
    }

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    companion object {
        private const val BASE_URL = "https://weather.example.test/VilageFcstInfoService_2.0"
        private const val SERVICE_KEY = "test-service-key"

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

        private val NCST_NO_HUMIDITY_WIND_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260708","baseTime":"1000","category":"T1H","nx":60,"ny":127,"obsrValue":"14.2"}
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

        private val VILAGE_FCST_4_DAYS_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260708","baseTime":"0500","category":"TMN","fcstDate":"20260708","fcstTime":"0600","fcstValue":"18"},
               {"baseDate":"20260708","baseTime":"0500","category":"TMX","fcstDate":"20260708","fcstTime":"1500","fcstValue":"29"},
               {"baseDate":"20260708","baseTime":"0500","category":"SKY","fcstDate":"20260708","fcstTime":"0900","fcstValue":"3"},
               {"baseDate":"20260708","baseTime":"0500","category":"PTY","fcstDate":"20260708","fcstTime":"0900","fcstValue":"0"},
               {"baseDate":"20260708","baseTime":"0500","category":"SKY","fcstDate":"20260708","fcstTime":"1200","fcstValue":"1"},
               {"baseDate":"20260708","baseTime":"0500","category":"PTY","fcstDate":"20260708","fcstTime":"1200","fcstValue":"0"},
               {"baseDate":"20260708","baseTime":"0500","category":"SKY","fcstDate":"20260708","fcstTime":"1800","fcstValue":"4"},
               {"baseDate":"20260708","baseTime":"0500","category":"PTY","fcstDate":"20260708","fcstTime":"1800","fcstValue":"0"},
               {"baseDate":"20260708","baseTime":"0500","category":"TMN","fcstDate":"20260709","fcstTime":"0600","fcstValue":"15"},
               {"baseDate":"20260708","baseTime":"0500","category":"TMX","fcstDate":"20260709","fcstTime":"1500","fcstValue":"25"},
               {"baseDate":"20260708","baseTime":"0500","category":"TMN","fcstDate":"20260710","fcstTime":"0600","fcstValue":"16"},
               {"baseDate":"20260708","baseTime":"0500","category":"TMX","fcstDate":"20260710","fcstTime":"1500","fcstValue":"26"},
               {"baseDate":"20260708","baseTime":"0500","category":"TMN","fcstDate":"20260711","fcstTime":"0600","fcstValue":"14"},
               {"baseDate":"20260708","baseTime":"0500","category":"TMX","fcstDate":"20260711","fcstTime":"1500","fcstValue":"24"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":14}}}
        """.trimIndent()

        private val VILAGE_FCST_5_DAYS_NO_TODAY_TMN_TMX_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"20260708","baseTime":"1700","category":"TMP","fcstDate":"20260708","fcstTime":"0900","fcstValue":"18"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMP","fcstDate":"20260708","fcstTime":"1200","fcstValue":"22"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMP","fcstDate":"20260708","fcstTime":"1500","fcstValue":"20"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMN","fcstDate":"20260709","fcstTime":"0600","fcstValue":"17"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMX","fcstDate":"20260709","fcstTime":"1500","fcstValue":"27"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMN","fcstDate":"20260710","fcstTime":"0600","fcstValue":"16"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMX","fcstDate":"20260710","fcstTime":"1500","fcstValue":"26"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMN","fcstDate":"20260711","fcstTime":"0600","fcstValue":"15"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMX","fcstDate":"20260711","fcstTime":"1500","fcstValue":"25"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMN","fcstDate":"20260712","fcstTime":"0600","fcstValue":"14"},
               {"baseDate":"20260708","baseTime":"1700","category":"TMX","fcstDate":"20260712","fcstTime":"1500","fcstValue":"24"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":11}}}
        """.trimIndent()

        fun vilageFcstBodyWithPop(fcstDate: String, closeTime: String, farTime: String): String = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"baseDate":"$fcstDate","baseTime":"0500","category":"POP","fcstDate":"$fcstDate","fcstTime":"$closeTime","fcstValue":"30"},
               {"baseDate":"$fcstDate","baseTime":"0500","category":"POP","fcstDate":"$fcstDate","fcstTime":"$farTime","fcstValue":"80"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":2}}}
        """.trimIndent()
    }
}
