package com.chamchamcham.application.policy.source.nongupez

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicReference

class JavaNetNongupEzHttpTransportTest {
    @Test
    fun `post sends JSON body and headers`() {
        val captured = AtomicReference<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/probe") { exchange ->
            val body = exchange.requestBody.readAllBytes().toString(UTF_8)
            captured.set(
                CapturedRequest(
                    contentType = exchange.requestHeaders.getFirst("Content-Type"),
                    accept = exchange.requestHeaders.getFirst("Accept"),
                    body = body
                )
            )
            val response = """{"ok":true}""".toByteArray(UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val transport = JavaNetNongupEzHttpTransport(
                "http://127.0.0.1:${server.address.port}",
                1000
            )

            val response = transport.post(
                "/probe",
                mapOf("srchCnd" to mapOf("srchBizYr" to "2026"))
            )

            assertThat(response).isEqualTo("""{"ok":true}""")
            assertThat(captured.get().contentType).startsWith("application/json")
            assertThat(captured.get().accept).isEqualTo("application/json")
            assertThat(captured.get().body).contains("\"srchCnd\":{\"srchBizYr\":\"2026\"}")
        } finally {
            server.stop(0)
        }
    }

    private data class CapturedRequest(
        val contentType: String?,
        val accept: String?,
        val body: String
    )
}
