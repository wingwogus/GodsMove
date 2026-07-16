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
 * n(= tmFc 날짜 기준 date까지의 일수) 경계를 계획(pure-sauteeing-perlis.md) §1 실측대로 검증한다.
 * 서울 좌표는 NearestMidFcstStationResolver가 taRegId=11B10101("서울")로 잡고,
 * MidTermLandRegion이 거기서 육상 구역코드 11B00000(서울.인천.경기)을 계산한다.
 */
class KmaMidTermForecastAdapterTest {
    private lateinit var server: MockRestServiceServer

    private val zone = ZoneId.of("Asia/Seoul")
    private val seoulLocation = WeatherLocation(
        latitude = 37.5665,
        longitude = 126.9780,
        roadAddress = "서울시 중구 세종대로 110",
        pnu = null
    )

    private fun adapterAt(now: LocalDateTime): KmaMidTermForecastAdapter {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val client = KmaApiClient(restClientBuilder.build(), SERVICE_KEY)
        val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
        return KmaMidTermForecastAdapter(client, KmaBaseTime(clock), properties())
    }

    @Test
    fun `tmFc가 오늘 06시면 n=4로 taMin4와 wf4Pm을 읽는다`() {
        val adapter = adapterAt(LocalDateTime.of(2026, 7, 15, 10, 0)) // tmFc=202607150600
        val date = LocalDate.of(2026, 7, 19) // tmFc날짜(07-15) 기준 +4

        server.expect(requestTo(taUri("11B10101", "202607150600")))
            .andRespond(withJson(itemsBody(mapOf("taMin4" to "24", "taMax4" to "30"))))
        server.expect(requestTo(landUri("11B00000", "202607150600")))
            .andRespond(withJson(itemsBody(mapOf("wf4Pm" to "흐리고 비"))))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNotNull
        assertThat(result!!.minTemperature).isEqualTo(24)
        assertThat(result.maxTemperature).isEqualTo(30)
        assertThat(result.condition).isEqualTo(WeatherCondition.RAIN)
        server.verify()
    }

    @Test
    fun `tmFc가 어제 18시면 n=5로 taMin5와 wf5Pm을 읽는다`() {
        val adapter = adapterAt(LocalDateTime.of(2026, 7, 15, 0, 5)) // tmFc=202607141800
        val date = LocalDate.of(2026, 7, 19) // tmFc날짜(07-14) 기준 +5

        server.expect(requestTo(taUri("11B10101", "202607141800")))
            .andRespond(withJson(itemsBody(mapOf("taMin5" to "24", "taMax5" to "30"))))
        server.expect(requestTo(landUri("11B00000", "202607141800")))
            .andRespond(withJson(itemsBody(mapOf("wf5Pm" to "흐림"))))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNotNull
        assertThat(result!!.minTemperature).isEqualTo(24)
        assertThat(result.maxTemperature).isEqualTo(30)
        assertThat(result.condition).isEqualTo(WeatherCondition.CLOUDY)
        server.verify()
    }

    @Test
    fun `date가 tmFc+3일이면 n=3이라 필드가 없어 null을 반환한다`() {
        val adapter = adapterAt(LocalDateTime.of(2026, 7, 15, 10, 0)) // tmFc=202607150600
        val date = LocalDate.of(2026, 7, 18) // tmFc날짜 기준 +3 (중기예보가 절대 안 주는 인덱스)

        server.expect(requestTo(taUri("11B10101", "202607150600")))
            .andRespond(withJson(itemsBody(emptyMap())))
        server.expect(requestTo(landUri("11B00000", "202607150600")))
            .andRespond(withJson(itemsBody(emptyMap())))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `n이 3에서 10 범위 밖이면 호출 없이 null을 반환한다`() {
        val adapter = adapterAt(LocalDateTime.of(2026, 7, 15, 10, 0)) // tmFc=202607150600
        val date = LocalDate.of(2026, 7, 15) // n=0

        val result = adapter.fetch(seoulLocation, date)

        // 등록된 요청 기대치가 없으므로, 실제로 HTTP 호출이 발생했다면 fetch()가 예외를 던졌을 것이다.
        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `wf{n}Pm이 없고 wf{n}Am만 있으면 Am을 사용한다`() {
        val adapter = adapterAt(LocalDateTime.of(2026, 7, 15, 10, 0)) // tmFc=202607150600
        val date = LocalDate.of(2026, 7, 19) // n=4

        server.expect(requestTo(taUri("11B10101", "202607150600")))
            .andRespond(withJson(itemsBody(mapOf("taMin4" to "20", "taMax4" to "26"))))
        server.expect(requestTo(landUri("11B00000", "202607150600")))
            .andRespond(withJson(itemsBody(mapOf("wf4Am" to "맑음"))))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNotNull
        assertThat(result!!.condition).isEqualTo(WeatherCondition.CLEAR)
        server.verify()
    }

    @Test
    fun `온도와 wf 둘 다 없으면 null을 반환한다`() {
        val adapter = adapterAt(LocalDateTime.of(2026, 7, 15, 10, 0)) // tmFc=202607150600
        val date = LocalDate.of(2026, 7, 19) // n=4

        server.expect(requestTo(taUri("11B10101", "202607150600")))
            .andRespond(withJson(itemsBody(emptyMap())))
        server.expect(requestTo(landUri("11B00000", "202607150600")))
            .andRespond(withJson(itemsBody(emptyMap())))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `getMidTa는 지점코드를, getMidLandFcst는 규칙으로 계산한 광역 구역코드를 사용한다`() {
        val adapter = adapterAt(LocalDateTime.of(2026, 7, 15, 10, 0)) // tmFc=202607150600
        val date = LocalDate.of(2026, 7, 19) // n=4

        // taRegId(11B10101)와 landRegId(11B00000)가 서로 다른 코드다 — 각 요청이 서로 다른
        // regId 값으로 정확히 매칭되지 않으면 MockRestServiceServer가 미기대 요청으로 실패시킨다.
        server.expect(requestTo(taUri("11B10101", "202607150600")))
            .andRespond(withJson(itemsBody(mapOf("taMin4" to "24", "taMax4" to "30"))))
        server.expect(requestTo(landUri("11B00000", "202607150600")))
            .andRespond(withJson(itemsBody(mapOf("wf4Pm" to "맑음"))))

        val result = adapter.fetch(seoulLocation, date)

        assertThat(result).isNotNull
        server.verify()
    }

    private fun taUri(regId: String, tmFc: String) =
        "$BASE_URL/getMidTa?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1&regId=$regId&tmFc=$tmFc&numOfRows=10"

    private fun landUri(regId: String, tmFc: String) =
        "$BASE_URL/getMidLandFcst?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1&regId=$regId&tmFc=$tmFc&numOfRows=10"

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun itemsBody(fields: Map<String, String>): String {
        val item = fields.entries.joinToString(",", prefix = "{", postfix = "}") { (key, value) -> "\"$key\":\"$value\"" }
        return """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":[$item]}}}}
        """.trimIndent()
    }

    private fun properties() = KmaProperties(
        serviceKey = SERVICE_KEY,
        baseUrl = KmaProperties.BaseUrl(
            vilageFcst = "https://weather.example.test/VilageFcstInfoService_2.0",
            midFcst = BASE_URL,
            asos = "https://weather.example.test/AsosDalyInfoService",
            uv = "https://weather.example.test/LivingWthrIdxServiceV5"
        )
    )

    companion object {
        private const val SERVICE_KEY = "test-service-key"
        private const val BASE_URL = "https://weather.example.test/MidFcstInfoService"
    }
}
