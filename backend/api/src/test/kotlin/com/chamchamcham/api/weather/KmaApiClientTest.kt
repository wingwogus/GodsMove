package com.chamchamcham.api.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

class KmaApiClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: KmaApiClient

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        client = KmaApiClient(restClientBuilder.build(), SERVICE_KEY)
    }

    @Test
    fun `resultCode 00이면 item 목록을 그대로 반환한다`() {
        server.expect(requestTo(requestUri("getUltraSrtNcst")))
            .andRespond(withJson(SUCCESS_BODY))

        val items = client.getItems(BASE_URL, "getUltraSrtNcst", PARAMS, TestItem::class.java)

        assertThat(items).hasSize(1)
        assertThat(items[0].category).isEqualTo("T1H")
        assertThat(items[0].obsrValue).isEqualTo("24.4")
        server.verify()
    }

    @Test
    fun `resultCode 03이면 예외 없이 빈 리스트를 반환한다`() {
        server.expect(requestTo(requestUri("getVilageFcst")))
            .andRespond(withJson(bodyOf(resultCode = "03", resultMsg = "NODATA_ERROR")))

        val items = client.getItems(BASE_URL, "getVilageFcst", PARAMS, TestItem::class.java)

        assertThat(items).isEmpty()
    }

    @Test
    fun `resultCode 99이면 예외 없이 빈 리스트를 반환한다`() {
        server.expect(requestTo(requestUri("getWthrDataList")))
            .andRespond(withJson(bodyOf(resultCode = "99", resultMsg = "전날 자료까지 제공됩니다")))

        val items = client.getItems(BASE_URL, "getWthrDataList", PARAMS, TestItem::class.java)

        assertThat(items).isEmpty()
    }

    @Test
    fun `resultCode 30이면 제공자 불가 예외를 던진다`() {
        server.expect(requestTo(requestUri("getMidTa")))
            .andRespond(withJson(bodyOf(resultCode = "30", resultMsg = "등록되지 않은 서비스키입니다")))

        val exception = assertThrows(BusinessException::class.java) {
            client.getItems(BASE_URL, "getMidTa", PARAMS, TestItem::class.java)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `HTTP 401이면 제공자 불가 예외를 던진다`() {
        server.expect(requestTo(requestUri("getUVIdxV5")))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = assertThrows(BusinessException::class.java) {
            client.getItems(BASE_URL, "getUVIdxV5", PARAMS, TestItem::class.java)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `item이 배열이 아니라 단일 객체로 와도 한 건짜리 리스트로 흡수한다`() {
        server.expect(requestTo(requestUri("getWthrDataList")))
            .andRespond(withJson(SINGLE_ITEM_BODY))

        val items = client.getItems(BASE_URL, "getWthrDataList", PARAMS, TestItem::class.java)

        assertThat(items).hasSize(1)
        assertThat(items[0].category).isEqualTo("T1H")
    }

    @Test
    fun `JSON을 요청해도 XML 오류 응답이 오면 제공자 불가 예외를 던진다`() {
        server.expect(requestTo(requestUri("getVilageFcst")))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.TEXT_XML)
                    .body("<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg></cmmMsgHeader></OpenAPI_ServiceResponse>")
            )

        val exception = assertThrows(BusinessException::class.java) {
            client.getItems(BASE_URL, "getVilageFcst", PARAMS, TestItem::class.java)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    private fun requestUri(operation: String) =
        "$BASE_URL/$operation?serviceKey=$SERVICE_KEY&dataType=JSON&pageNo=1&numOfRows=10&nx=60&ny=127"

    private fun withJson(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    private data class TestItem(
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("obsrValue") val obsrValue: String? = null
    )

    companion object {
        private const val BASE_URL = "https://weather.example.test/VilageFcstInfoService_2.0"
        private const val SERVICE_KEY = "test-service-key"
        private val PARAMS = linkedMapOf("numOfRows" to "10", "nx" to "60", "ny" to "127")

        private fun bodyOf(resultCode: String, resultMsg: String) = """
            {"response":{"header":{"resultCode":"$resultCode","resultMsg":"$resultMsg"},
             "body":{"dataType":"JSON","items":{"item":[]},"pageNo":1,"numOfRows":10,"totalCount":0}}}
        """.trimIndent()

        private val SUCCESS_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"category":"T1H","obsrValue":"24.4"}
             ]},"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()

        private val SINGLE_ITEM_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":
               {"category":"T1H","obsrValue":"25.2"}
             },"pageNo":1,"numOfRows":10,"totalCount":1}}}
        """.trimIndent()
    }
}
