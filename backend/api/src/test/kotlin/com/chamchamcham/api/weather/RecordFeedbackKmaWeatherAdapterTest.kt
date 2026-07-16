package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 옛 KmaWeatherProvider.fetch()(dev, 코칭/기록피드백용)의 집계·위험플래그 로직을
 * 새 아키텍처 위로 이관했는지 검증한다. `MockRestServiceServer.bindTo` 패턴은
 * KmaCurrentObservationAdapterTest/KmaShortTermForecastAdapterTest를 따른다.
 */
class RecordFeedbackKmaWeatherAdapterTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: RecordFeedbackKmaWeatherAdapter

    private fun setUp(fixedTime: LocalDateTime) {
        val clock = Clock.fixed(fixedTime.atZone(SEOUL).toInstant(), SEOUL)
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val client = KmaApiClient(restClientBuilder.build(), SERVICE_KEY)
        val currentObservationPort = KmaCurrentObservationAdapter(client, KmaBaseTime(clock), properties())
        adapter = RecordFeedbackKmaWeatherAdapter(currentObservationPort, client, KmaBaseTime(clock), properties(), clock)
    }

    @Test
    fun `실황에 강수가 없으면 동네예보 SKY로 현재 하늘상태를 보완하고 일별 값을 집계한다`() {
        setUp(FIXED_TIME)
        server.expect(requestTo(ncstUri("20260711", "0900")))
            .andRespond(withJson(ncstBodyOf(temperature = "14.2", pty = "0")))
        server.expect(requestTo(vilageFcstUri("20260711", "0800")))
            .andRespond(withJson(THREE_DAYS_BODY))

        val result = adapter.fetch(37.5665, 126.9780, limitDays = 7)

        assertThat(result.current.temperatureC).isEqualTo(14)
        assertThat(result.current.skyCondition).isEqualTo("맑음") // 가장 가까운 09시 슬롯의 SKY=1
        assertThat(result.current.observedAt).isEqualTo(LocalDateTime.of(2026, 7, 11, 9, 0))
        assertThat(result.source).isEqualTo("KMA_SHORT_TERM")
        assertThat(result.forecastDays.map { it.date.toString() })
            .containsExactly("2026-07-11", "2026-07-12", "2026-07-13")

        val day0 = result.forecastDays[0]
        assertThat(day0.rainfallMm).isEqualByComparingTo("22.5")
        assertThat(day0.rainProbabilityPct).isEqualTo(80)
        assertThat(day0.maxTemperatureC).isEqualByComparingTo("31")
        assertThat(day0.minTemperatureC).isEqualByComparingTo("24")
        assertThat(day0.humidityPct).isEqualByComparingTo("88")
        assertThat(day0.windSpeedMs).isEqualByComparingTo("9.1")
        assertThat(day0.riskFlags).containsExactly("RAIN", "HEAVY_RAIN", "HIGH_HUMIDITY", "HOT", "STRONG_WIND")

        // day1: 강수형태(PTY=1)만 있고 나머지 임계값은 안 넘는다 -> RAIN만.
        assertThat(result.forecastDays[1].riskFlags).containsExactly("RAIN")
        // day2: 강수형태도 없고 강수확률도 60% 미만 -> 위험 신호 없음.
        assertThat(result.forecastDays[2].riskFlags).isEmpty()
        server.verify()
    }

    @Test
    fun `실황에 강수형태가 있으면 동네예보 SKY보다 우선한다`() {
        setUp(FIXED_TIME)
        server.expect(requestTo(ncstUri("20260711", "0900")))
            .andRespond(withJson(ncstBodyOf(temperature = "14.2", pty = "1")))
        server.expect(requestTo(vilageFcstUri("20260711", "0800")))
            .andRespond(withJson(THREE_DAYS_BODY)) // SKY=1(맑음)이 있지만 실황 PTY=1(비)이 우선해야 한다

        val result = adapter.fetch(37.5665, 126.9780, limitDays = 1)

        assertThat(result.current.skyCondition).isEqualTo("비")
    }

    @Test
    fun `정성 강수량은 숫자로 만들지 않고 강수확률과 강수형태만 위험 신호에 반영한다`() {
        setUp(FIXED_TIME)
        server.expect(requestTo(ncstUri("20260711", "0900")))
            .andRespond(withJson(ncstBodyOf(temperature = "14.2", pty = "0")))
        server.expect(requestTo(vilageFcstUri("20260711", "0800")))
            .andRespond(withJson(QUALITATIVE_PCP_BODY))

        val result = adapter.fetch(37.5665, 126.9780, limitDays = 1)

        assertThat(result.forecastDays).hasSize(1)
        assertThat(result.forecastDays.first().rainfallMm).isNull()
        assertThat(result.forecastDays.first().rainProbabilityPct).isEqualTo(70)
        assertThat(result.forecastDays.first().riskFlags).containsExactly("RAIN")
    }

    @Test
    fun `동네예보 자료가 없어도 예보 없이 현재 날씨는 반환한다`() {
        // 새 아키텍처의 KmaApiClient는 자료없음(03/99)을 예외가 아니라 빈 리스트로 다룬다 —
        // 단기예보는 선택 소스라는 원칙(계획 §5)을 record-feedback에도 그대로 적용한다.
        setUp(FIXED_TIME)
        server.expect(requestTo(ncstUri("20260711", "0900")))
            .andRespond(withJson(ncstBodyOf(temperature = "14.2", pty = "0")))
        server.expect(requestTo(vilageFcstUri("20260711", "0800")))
            .andRespond(withJson(emptyBodyOf(resultCode = "03")))

        val result = adapter.fetch(37.5665, 126.9780, limitDays = 7)

        assertThat(result.current.temperatureC).isEqualTo(14)
        assertThat(result.current.skyCondition).isEqualTo("정보없음")
        assertThat(result.forecastDays).isEmpty()
    }

    @Test
    fun `limitDays 범위를 벗어나면 조회 없이 예외를 던진다`() {
        setUp(FIXED_TIME)

        assertThatThrownBy { adapter.fetch(37.5665, 126.9780, limitDays = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { adapter.fetch(37.5665, 126.9780, limitDays = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun ncstUri(baseDate: String, baseTime: String) =
        "$BASE_URL/getUltraSrtNcst?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1" +
            "&numOfRows=100&base_date=$baseDate&base_time=$baseTime&nx=60&ny=127"

    private fun vilageFcstUri(baseDate: String, baseTime: String) =
        "$BASE_URL/getVilageFcst?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1" +
            "&numOfRows=2000&base_date=$baseDate&base_time=$baseTime&nx=60&ny=127"

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun ncstBodyOf(temperature: String, pty: String) = """
        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
         "body":{"dataType":"JSON","items":{"item":[
           {"baseDate":"20260711","baseTime":"0900","category":"T1H","obsrValue":"$temperature"},
           {"baseDate":"20260711","baseTime":"0900","category":"PTY","obsrValue":"$pty"}
         ]},"pageNo":1,"numOfRows":100,"totalCount":2}}}
    """.trimIndent()

    private fun emptyBodyOf(resultCode: String) = """
        {"response":{"header":{"resultCode":"$resultCode","resultMsg":"NODATA_ERROR"},
         "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":1000,"totalCount":0}}}
    """.trimIndent()

    private fun properties() = KmaProperties(
        serviceKey = SERVICE_KEY,
        baseUrl = KmaProperties.BaseUrl(
            vilageFcst = BASE_URL,
            midFcst = "https://weather.example.test/MidFcstInfoService",
            asos = "https://weather.example.test/AsosDalyInfoService",
            uv = "https://weather.example.test/LivingWthrIdxServiceV5"
        )
    )

    companion object {
        private const val BASE_URL = "https://weather.example.test/VilageFcstInfoService_2.0"
        private const val SERVICE_KEY = "test-service-key"
        private val SEOUL = ZoneId.of("Asia/Seoul")

        // 10:00 KST -> resolveNcst()는 09:00(정시 40분 버퍼), resolveLatest()는 08:00(스케줄 8시,+10분 버퍼).
        private val FIXED_TIME = LocalDateTime.of(2026, 7, 11, 10, 0)

        private val THREE_DAYS_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"category":"SKY","fcstDate":"20260711","fcstTime":"0900","fcstValue":"1"},
               {"category":"TMN","fcstDate":"20260711","fcstTime":"0600","fcstValue":"24"},
               {"category":"TMX","fcstDate":"20260711","fcstTime":"1500","fcstValue":"31"},
               {"category":"POP","fcstDate":"20260711","fcstTime":"1200","fcstValue":"80"},
               {"category":"REH","fcstDate":"20260711","fcstTime":"1200","fcstValue":"88"},
               {"category":"WSD","fcstDate":"20260711","fcstTime":"1200","fcstValue":"9.1"},
               {"category":"PTY","fcstDate":"20260711","fcstTime":"1200","fcstValue":"1"},
               {"category":"PCP","fcstDate":"20260711","fcstTime":"1200","fcstValue":"22.5mm"},
               {"category":"TMN","fcstDate":"20260712","fcstTime":"0600","fcstValue":"20"},
               {"category":"TMX","fcstDate":"20260712","fcstTime":"1500","fcstValue":"27"},
               {"category":"POP","fcstDate":"20260712","fcstTime":"1200","fcstValue":"40"},
               {"category":"PTY","fcstDate":"20260712","fcstTime":"1200","fcstValue":"1"},
               {"category":"REH","fcstDate":"20260712","fcstTime":"1200","fcstValue":"70"},
               {"category":"WSD","fcstDate":"20260712","fcstTime":"1200","fcstValue":"3.5"},
               {"category":"PCP","fcstDate":"20260712","fcstTime":"1200","fcstValue":"강수없음"},
               {"category":"TMN","fcstDate":"20260713","fcstTime":"0600","fcstValue":"21"},
               {"category":"TMX","fcstDate":"20260713","fcstTime":"1500","fcstValue":"29"},
               {"category":"POP","fcstDate":"20260713","fcstTime":"1200","fcstValue":"20"},
               {"category":"PTY","fcstDate":"20260713","fcstTime":"1200","fcstValue":"0"},
               {"category":"REH","fcstDate":"20260713","fcstTime":"1200","fcstValue":"60"},
               {"category":"WSD","fcstDate":"20260713","fcstTime":"1200","fcstValue":"2.5"},
               {"category":"PCP","fcstDate":"20260713","fcstTime":"1200","fcstValue":"0"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":21}}}
        """.trimIndent()

        private val QUALITATIVE_PCP_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"category":"TMN","fcstDate":"20260711","fcstTime":"0600","fcstValue":"22"},
               {"category":"TMX","fcstDate":"20260711","fcstTime":"1500","fcstValue":"28"},
               {"category":"POP","fcstDate":"20260711","fcstTime":"1200","fcstValue":"70"},
               {"category":"PTY","fcstDate":"20260711","fcstTime":"1200","fcstValue":"0"},
               {"category":"PCP","fcstDate":"20260711","fcstTime":"1200","fcstValue":"1.0mm 미만"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":5}}}
        """.trimIndent()
    }
}
