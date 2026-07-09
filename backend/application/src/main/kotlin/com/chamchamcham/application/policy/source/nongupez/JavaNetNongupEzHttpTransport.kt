package com.chamchamcham.application.policy.source.nongupez

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
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
    private val objectMapper = jacksonObjectMapper()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMillis))
        .build()

    override fun post(path: String, body: Map<String, Any?>): String {
        val requestBody = objectMapper.writeValueAsString(body)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("NongupEZ HTTP ${response.statusCode()} for $path")
        }
        return response.body()
    }
}
