package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.WeatherCondition
import com.chamchamcham.application.weather.WeatherLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
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
 * 초단기실황(getUltraSrtNcst) 어댑터. `MockRestServiceServer.bindTo` 패턴은
 * KmaApiClientTest를 그대로 따른다. 시각은 Clock.fixed로 고정한다.
 */
class KmaCurrentObservationAdapterTest {

    private val location = WeatherLocation(
        latitude = 37.5665,
        longitude = 126.9780,
        roadAddress = "서울 중구 세종대로 110",
        pnu = null
    )

    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: KmaCurrentObservationAdapter

    private fun setUp(fixedTime: LocalDateTime) {
        val clock = Clock.fixed(fixedTime.atZone(SEOUL).toInstant(), SEOUL)
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val client = KmaApiClient(restClientBuilder.build(), SERVICE_KEY)
        adapter = KmaCurrentObservationAdapter(client, KmaBaseTime(clock), properties())
    }

    @Test
    fun `T1H REH WSD를 정상 파싱하고 PTY=0이면 강수형태는 null이다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2100")))
            .andRespond(
                withJson(
                    bodyOf(
                        item("T1H", "24.4"),
                        item("REH", "85"),
                        item("WSD", "1.7"),
                        item("PTY", "0")
                    )
                )
            )

        val result = adapter.fetch(location)

        assertThat(result.temperature).isEqualTo(24)
        assertThat(result.humidity).isEqualTo(85)
        assertThat(result.windSpeed).isEqualTo(1.7)
        assertThat(result.precipitationType).isNull()
    }

    @Test
    fun `PTY=1이면 RAIN이다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2100")))
            .andRespond(withJson(bodyOf(item("T1H", "24.4"), item("PTY", "1"))))

        val result = adapter.fetch(location)

        assertThat(result.precipitationType).isEqualTo(WeatherCondition.RAIN)
    }

    @Test
    fun `PTY가 0이 아닌 알 수 없는 코드면 강수형태는 null이다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2100")))
            .andRespond(withJson(bodyOf(item("T1H", "24.4"), item("PTY", "9"))))

        val result = adapter.fetch(location)

        assertThat(result.precipitationType).isNull()
    }

    @Test
    fun `T1H가 없으면 WEATHER_002를 던진다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2100")))
            .andRespond(withJson(bodyOf(item("REH", "85"))))

        val exception = assertThrows(BusinessException::class.java) { adapter.fetch(location) }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `items가 빈 리스트면 WEATHER_002를 던진다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2100")))
            .andRespond(withJson(emptyBodyOf(resultCode = "03")))

        val exception = assertThrows(BusinessException::class.java) { adapter.fetch(location) }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `observedAt은 baseDate와 baseTime을 합쳐 파싱한다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2100")))
            .andRespond(withJson(bodyOf(item("T1H", "24.4", baseDate = "20260715", baseTime = "2100"))))

        val result = adapter.fetch(location)

        assertThat(result.observedAt).isEqualTo(LocalDateTime.of(2026, 7, 15, 21, 0))
    }

    private fun requestUri(baseDate: String, baseTime: String) =
        "$BASE_URL/getUltraSrtNcst?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1" +
            "&numOfRows=100&base_date=$baseDate&base_time=$baseTime&nx=60&ny=127"

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun item(category: String, obsrValue: String, baseDate: String = "20260715", baseTime: String = "2100") =
        """{"baseDate":"$baseDate","baseTime":"$baseTime","category":"$category","obsrValue":"$obsrValue"}"""

    private fun bodyOf(vararg items: String) = """
        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
         "body":{"dataType":"JSON","items":{"item":[${items.joinToString(",")}]},
         "pageNo":1,"numOfRows":100,"totalCount":${items.size}}}}
    """.trimIndent()

    private fun emptyBodyOf(resultCode: String) = """
        {"response":{"header":{"resultCode":"$resultCode","resultMsg":"NODATA_ERROR"},
         "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":100,"totalCount":0}}}
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
    }
}
