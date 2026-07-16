package com.chamchamcham.api.weather

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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * areaNo가 법정동 코드(pnu 앞 10자리)가 아니라 시군구 코드(pnu 앞 5자리 + "00000")여야 한다는
 * 계획(pure-sauteeing-perlis.md) §1 실측을 검증한다.
 */
class KmaUvIndexAdapterTest {
    private lateinit var server: MockRestServiceServer

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 7, 15, 10, 0) // resolveUv() -> "2026071509"

    private fun adapter(): KmaUvIndexAdapter {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val client = KmaApiClient(restClientBuilder.build(), SERVICE_KEY)
        val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
        return KmaUvIndexAdapter(client, KmaBaseTime(clock), properties())
    }

    private fun locationWithPnu(pnu: String?) = WeatherLocation(
        latitude = 37.5665,
        longitude = 126.9780,
        roadAddress = "서울시 중구 세종대로 110",
        pnu = pnu
    )

    @Test
    fun `pnu 앞 10자리(법정동코드)가 아니라 앞 5자리+00000(시군구코드)으로 요청한다`() {
        val target = adapter()
        val location = locationWithPnu("1114010300xxxxxxxxx")

        server.expect(requestTo(uvUri("1114000000", "2026071509")))
            .andRespond(withJson(itemsBody(mapOf("h0" to "1"))))

        val result = target.fetch(location)

        assertThat(result).isEqualTo(1)
        server.verify()
    }

    @Test
    fun `h0을 정수로 파싱한다`() {
        val target = adapter()
        val location = locationWithPnu("1114010300xxxxxxxxx")

        server.expect(requestTo(uvUri("1114000000", "2026071509")))
            .andRespond(withJson(itemsBody(mapOf("h0" to "5"))))

        assertThat(target.fetch(location)).isEqualTo(5)
    }

    @Test
    fun `pnu가 null이면 호출 없이 null을 반환한다`() {
        val target = adapter()
        val location = locationWithPnu(null)

        val result = target.fetch(location)

        // 등록된 요청 기대치가 없으므로, 실제로 HTTP 호출이 발생했다면 fetch()가 예외를 던졌을 것이다.
        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `pnu가 5자리 미만이면 null을 반환한다`() {
        val target = adapter()
        val location = locationWithPnu("1114")

        val result = target.fetch(location)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `items가 빈 리스트면 null을 반환한다`() {
        val target = adapter()
        val location = locationWithPnu("1114010300xxxxxxxxx")

        server.expect(requestTo(uvUri("1114000000", "2026071509")))
            .andRespond(withJson(itemsBody(emptyMap(), hasItem = false)))

        val result = target.fetch(location)

        assertThat(result).isNull()
        server.verify()
    }

    private fun uvUri(areaNo: String, time: String) =
        "$BASE_URL/getUVIdxV5?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1&areaNo=$areaNo&time=$time&numOfRows=10"

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
            asos = "https://weather.example.test/AsosDalyInfoService",
            uv = BASE_URL
        )
    )

    companion object {
        private const val SERVICE_KEY = "test-service-key"
        private const val BASE_URL = "https://weather.example.test/LivingWthrIdxServiceV5"
    }
}
