package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition
import com.chamchamcham.application.weather.WeatherLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.time.LocalDate

/**
 * 서울 좌표는 NearestAsosStationResolver가 stnId="108"("서울")로 최근접 매칭한다.
 * sumRn=''(무강수)이 비 아님으로 처리되는 것이 계획(pure-sauteeing-perlis.md) §1의 실측 확정 사실이다.
 */
class KmaHistoricalObservationAdapterTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: KmaHistoricalObservationAdapter

    private val seoulLocation = WeatherLocation(
        latitude = 37.5665,
        longitude = 126.9780,
        roadAddress = "서울시 중구 세종대로 110",
        pnu = null
    )
    private val date = LocalDate.of(2026, 7, 14)

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val client = KmaApiClient(restClientBuilder.build(), SERVICE_KEY)
        adapter = KmaHistoricalObservationAdapter(client, properties())
    }

    @Test
    fun `정상 응답이면 최저최고와 상태를 파싱한다`() {
        server.expect(requestTo(requestUri()))
            .andRespond(withJson(itemsBody(mapOf("minTa" to "25.2", "maxTa" to "31.0", "avgTca" to "8.9", "sumRn" to "74.5"))))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNotNull
        assertThat(result!!.minTemperature).isEqualTo(25)
        assertThat(result.maxTemperature).isEqualTo(31)
        assertThat(result.condition).isEqualTo(WeatherCondition.RAIN)
        server.verify()
    }

    @Test
    fun `sumRn이 빈 문자열이면 무강수로 판정해 전운량으로 상태를 정한다`() {
        server.expect(requestTo(requestUri()))
            .andRespond(withJson(itemsBody(mapOf("minTa" to "20.0", "maxTa" to "26.0", "avgTca" to "8.9", "sumRn" to ""))))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNotNull
        assertThat(result!!.condition).isEqualTo(WeatherCondition.CLOUDY)
        server.verify()
    }

    @Test
    fun `minTa가 없으면 null을 반환한다`() {
        server.expect(requestTo(requestUri()))
            .andRespond(withJson(itemsBody(mapOf("maxTa" to "31.0", "avgTca" to "8.9", "sumRn" to "0"))))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `items가 빈 리스트면(오늘 조회 99) null을 반환한다`() {
        server.expect(requestTo(requestUri()))
            .andRespond(withJson(itemsBody(emptyMap(), hasItem = false)))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `요청 파라미터는 startDt와 endDt가 같고 최근접 stnIds를 사용한다`() {
        // requestUri()가 startDt=endDt=20260714, stnIds=108을 요구하므로, 이 매칭 자체가 검증이다.
        server.expect(requestTo(requestUri()))
            .andRespond(withJson(itemsBody(mapOf("minTa" to "25.2", "maxTa" to "31.0", "avgTca" to "8.9", "sumRn" to ""))))

        adapter.fetch(seoulLocation, date)

        server.verify()
    }

    private fun requestUri() =
        "$BASE_URL/getWthrDataList?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1" +
            "&dataCd=ASOS&dateCd=DAY&startDt=20260714&endDt=20260714&stnIds=108&numOfRows=10"

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun itemsBody(fields: Map<String, String>, hasItem: Boolean = true): String {
        val items = if (hasItem) {
            val item = fields.entries.joinToString(",", prefix = "{", postfix = "}") { (key, value) -> "\"$key\":\"$value\"" }
            "[$item]"
        } else {
            "[]"
        }
        return """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":$items}}}}
        """.trimIndent()
    }

    private fun properties() = KmaProperties(
        serviceKey = SERVICE_KEY,
        baseUrl = KmaProperties.BaseUrl(
            vilageFcst = "https://weather.example.test/VilageFcstInfoService_2.0",
            midFcst = "https://weather.example.test/MidFcstInfoService",
            asos = BASE_URL,
            uv = "https://weather.example.test/LivingWthrIdxServiceV5"
        )
    )

    companion object {
        private const val SERVICE_KEY = "test-service-key"
        private const val BASE_URL = "https://weather.example.test/AsosDalyInfoService"
    }
}
