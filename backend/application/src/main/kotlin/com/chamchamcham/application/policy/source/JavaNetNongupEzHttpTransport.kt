package com.chamchamcham.application.policy.source

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
class JavaNetNongupEzHttpTransport(
    @Value("\${policy.nongup-ez.base-url:https://www.nongupez.go.kr}") private val baseUrl: String,
    @Value("\${policy.nongup-ez.timeout-millis:10000}") private val timeoutMillis: Long
) : NongupEzHttpTransport {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMillis))
        .build()

    override fun post(path: String, form: Map<String, String>): String {
        val encodedForm = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(encodedForm))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("NongupEZ HTTP ${response.statusCode()} for $path")
        }
        return response.body()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
