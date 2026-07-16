package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition
import com.chamchamcham.application.weather.WeatherLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 단기예보(getVilageFcst) 어댑터. `MockRestServiceServer.bindTo` 패턴은 KmaApiClientTest를
 * 따른다. §3 회귀 버그(TMN/TMX 없을 때 TMP로 폴백)와 §2.1 날짜 경계 최근접 슬롯을 검증한다.
 */
class KmaShortTermForecastAdapterTest {

    private val location = WeatherLocation(
        latitude = 37.5665,
        longitude = 126.9780,
        roadAddress = "서울 중구 세종대로 110",
        pnu = null
    )

    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: KmaShortTermForecastAdapter

    private fun setUp(fixedTime: LocalDateTime) {
        val clock = Clock.fixed(fixedTime.atZone(SEOUL).toInstant(), SEOUL)
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val client = KmaApiClient(restClientBuilder.build(), SERVICE_KEY)
        adapter = KmaShortTermForecastAdapter(client, KmaBaseTime(clock), properties(), clock)
    }

    @Test
    fun `TMN TMX가 없으면 TMP가 여러 개 있어도 최저최고는 null이다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2000")))
            .andRespond(
                withJson(
                    bodyOf(
                        item("TMP", "24", "20260715", "2100"),
                        item("TMP", "23", "20260715", "2200"),
                        item("TMP", "22", "20260715", "2300"),
                        item("SKY", "1", "20260715", "2100"),
                        item("PTY", "0", "20260715", "2100")
                    )
                )
            )

        val result = adapter.fetchLatest(location)

        assertThat(result).isNotNull
        val today = result!!.dailyForecasts.single { it.date == LocalDate.of(2026, 7, 15) }
        assertThat(today.minTemperature).isNull()
        assertThat(today.maxTemperature).isNull()
    }

    @Test
    fun `fetchTodayRange는 오늘 TMN TMX를 뽑는다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 10, 0))
        server.expect(requestTo(requestUri("20260715", "0200")))
            .andRespond(
                withJson(
                    bodyOf(
                        item("TMN", "25.0", "20260715", "0600"),
                        item("TMX", "29.0", "20260715", "1500"),
                        item("SKY", "1", "20260715", "1200"),
                        item("PTY", "0", "20260715", "1200")
                    )
                )
            )

        val result = adapter.fetchTodayRange(location)

        assertThat(result).isNotNull
        assertThat(result!!.date).isEqualTo(LocalDate.of(2026, 7, 15))
        assertThat(result.minTemperature).isEqualTo(25)
        assertThat(result.maxTemperature).isEqualTo(29)
    }

    @Test
    fun `fetchTodayRange에 오늘 항목이 없으면 null이다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 10, 0))
        server.expect(requestTo(requestUri("20260715", "0200")))
            .andRespond(withJson(bodyOf(item("TMN", "10.0", "20260716", "0600"))))

        val result = adapter.fetchTodayRange(location)

        assertThat(result).isNull()
    }

    @Test
    fun `currentSky는 당일 항목이 없어도 날짜 경계를 넘어 최근접 슬롯을 고른다`() {
        // 23시 발표엔 당일 항목이 0개다(계획 §1). 23:30에 다음날 0000 슬롯만 있으면 그걸 써야 한다.
        setUp(LocalDateTime.of(2026, 7, 15, 23, 30))
        server.expect(requestTo(requestUri("20260715", "2300")))
            .andRespond(
                withJson(
                    bodyOf(
                        item("SKY", "4", "20260716", "0000"),
                        item("PTY", "0", "20260716", "0000")
                    )
                )
            )

        val result = adapter.fetchLatest(location)

        assertThat(result).isNotNull
        assertThat(result!!.currentSky).isEqualTo(WeatherCondition.CLOUDY)
    }

    @Test
    fun `items가 빈 리스트면 fetchLatest는 null을 반환한다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2000")))
            .andRespond(withJson(emptyBodyOf(resultCode = "03")))

        val result = adapter.fetchLatest(location)

        assertThat(result).isNull()
    }

    @Test
    fun `dailyForecasts는 fcstDate별로 묶이고 날짜 오름차순이다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 21, 50))
        server.expect(requestTo(requestUri("20260715", "2000")))
            .andRespond(
                withJson(
                    bodyOf(
                        item("TMP", "20", "20260717", "0900"),
                        item("TMP", "21", "20260715", "2100"),
                        item("TMP", "22", "20260716", "0900")
                    )
                )
            )

        val result = adapter.fetchLatest(location)

        assertThat(result).isNotNull
        assertThat(result!!.dailyForecasts.map { it.date }).containsExactly(
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 16),
            LocalDate.of(2026, 7, 17)
        )
    }

    @Test
    fun `condition은 정오에 가장 가까운 fcstTime 슬롯에서 온다`() {
        setUp(LocalDateTime.of(2026, 7, 15, 10, 0))
        server.expect(requestTo(requestUri("20260715", "0200")))
            .andRespond(
                withJson(
                    bodyOf(
                        item("SKY", "1", "20260715", "0900"),
                        item("PTY", "0", "20260715", "0900"),
                        item("SKY", "3", "20260715", "1100"),
                        item("PTY", "0", "20260715", "1100"),
                        item("SKY", "4", "20260715", "1500"),
                        item("PTY", "0", "20260715", "1500")
                    )
                )
            )

        val result = adapter.fetchTodayRange(location)

        assertThat(result).isNotNull
        // 1100이 정오(1200)에서 1시간, 0900은 3시간, 1500은 3시간이므로 1100의 SKY=3(구름많음)을 써야 한다.
        assertThat(result!!.condition).isEqualTo(WeatherCondition.PARTLY_CLOUDY)
    }

    private fun requestUri(baseDate: String, baseTime: String) =
        "$BASE_URL/getVilageFcst?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1" +
            "&numOfRows=1000&base_date=$baseDate&base_time=$baseTime&nx=60&ny=127"

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun item(category: String, fcstValue: String, fcstDate: String, fcstTime: String) =
        """{"category":"$category","fcstDate":"$fcstDate","fcstTime":"$fcstTime","fcstValue":"$fcstValue"}"""

    private fun bodyOf(vararg items: String) = """
        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
         "body":{"dataType":"JSON","items":{"item":[${items.joinToString(",")}]},
         "pageNo":1,"numOfRows":1000,"totalCount":${items.size}}}}
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
    }
}
