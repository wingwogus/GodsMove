package com.chamchamcham.application.pesticide.sync

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * PSIS(농약안전정보시스템) 농약등록정보 API 호출 어댑터.
 *
 * base-url은 확정된 실제 엔드포인트가 아니라 자리표시자(placeholder)다. PSIS에서 API 키가
 * 발급되면 마이페이지에 표시되는 실제 요청 URL로 psis.pesticide.base-url 값을 교체해야 한다.
 * serviceKey는 공공데이터포털 인코딩 키가 그대로 전달되도록 pre-encoded URI로 붙인다(이중 인코딩 방지,
 * KmaWeatherProvider와 동일한 관례).
 */
@Component
class JavaNetPsisPesticideHttpTransport(
    @Value("\${psis.pesticide.base-url:}") private val baseUrl: String,
    @Value("\${psis.pesticide.service-key:}") private val serviceKey: String,
    @Value("\${psis.pesticide.timeout-millis:10000}") private val timeoutMillis: Long,
) : PsisPesticideHttpTransport {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMillis))
            .build()
    }

    override fun get(queryParams: Map<String, String>): String {
        check(baseUrl.isNotBlank()) { "psis.pesticide.base-url is not configured" }
        check(serviceKey.isNotBlank()) { "psis.pesticide.service-key is not configured" }

        val query = queryParams.entries.joinToString("&") { (key, value) -> "$key=$value" }
        val uri = URI.create("$baseUrl?serviceKey=$serviceKey&$query")

        val request = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Accept", "application/xml")
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("PSIS pesticide HTTP ${response.statusCode()}")
        }
        return response.body()
    }
}
