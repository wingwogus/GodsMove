package com.chamchamcham.api.rag.client

import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.config.OpenClawProperties
import com.chamchamcham.config.SpringAiRagConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.full.memberProperties
import kotlin.system.measureTimeMillis

class OpenClawChatModelTest {
    @Test
    fun `RAG properties do not own HTTP transport timeout`() {
        assertThat(RagProperties::class.memberProperties.map { it.name })
            .doesNotContain("timeoutMillis")
    }

    @Test
    fun `all profiles configure OpenClaw transport timeouts`() {
        val profiles = listOf(
            "application-local.yml",
            "application-dev.yml",
            "application-prod.yml",
            "application-test.yml"
        )

        profiles.forEach { profile ->
            val properties = YamlPropertySourceLoader()
                .load(profile, ClassPathResource(profile))
                .single()

            assertThat(properties.getProperty("rag.timeout-millis"))
                .describedAs("%s must not configure the removed RAG timeout", profile)
                .isNull()
            assertThat(properties.getProperty("openclaw.connect-timeout-millis"))
                .describedAs("%s OpenClaw connect timeout", profile)
                .isEqualTo("\${OPENCLAW_CONNECT_TIMEOUT_MILLIS:3000}")
            assertThat(properties.getProperty("openclaw.read-timeout-millis"))
                .describedAs("%s OpenClaw read timeout", profile)
                .isEqualTo("\${OPENCLAW_READ_TIMEOUT_MILLIS:30000}")
        }
    }

    @Test
    fun `OpenClaw timeout defaults are positive and bounded`() {
        val properties = OpenClawProperties()
        val values = OpenClawProperties::class.memberProperties.associate { property ->
            property.name to property.get(properties)
        }

        assertThat(values)
            .containsEntry("connectTimeoutMillis", 3_000)
            .containsEntry("readTimeoutMillis", 30_000)
    }

    @Test
    fun `call stops waiting when OpenClaw exceeds the configured read timeout`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange ->
                Thread.sleep(1_000)
                val body = """{"choices":[{"message":{"role":"assistant","content":"late"}}]}"""
                    .toByteArray()
                exchange.responseHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        try {
            contextRunner
                .withPropertyValues(
                    "openclaw.base-url=http://127.0.0.1:${server.address.port}",
                    "openclaw.api-key=test-key",
                    "openclaw.agent-id=agent-1",
                    "openclaw.read-timeout-millis=100"
                )
                .run { context ->
                    val model = context.getBean(OpenClawChatModel::class.java)

                    val elapsedMillis = measureTimeMillis {
                        assertThatThrownBy { model.call(Prompt(UserMessage("안녕"))) }
                            .isInstanceOfSatisfying(BusinessException::class.java) {
                                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_CHAT_UNAVAILABLE)
                            }
                    }

                    assertThat(elapsedMillis).isLessThan(800)
                }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `call sends auth and agent headers to OpenClaw`() {
        val authorization = AtomicReference<String>()
        val agentId = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange ->
                authorization.set(exchange.requestHeaders.getFirst(HttpHeaders.AUTHORIZATION))
                agentId.set(exchange.requestHeaders.getFirst("x-openclaw-agent-id"))
                requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
                val body = """{"choices":[{"message":{"role":"assistant","content":"{\"summary\":\"ok\"}"}}]}"""
                    .toByteArray()
                exchange.responseHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        try {
            contextRunner
                .withPropertyValues(
                    "openclaw.base-url=http://127.0.0.1:${server.address.port}",
                    "openclaw.api-key=test-key",
                    "openclaw.agent-id=agent-1"
                )
                .run { context ->
                    val model = context.getBean(OpenClawChatModel::class.java)
                    val response = model.call(Prompt(UserMessage("안녕")))

                    assertThat(response.result.output.text).contains("summary")
                }
            assertThat(authorization.get()).isEqualTo("Bearer test-key")
            assertThat(agentId.get()).isEqualTo("agent-1")
            assertThat(jacksonObjectMapper().readTree(requestBody.get())).isEqualTo(
                jacksonObjectMapper().readTree(
                    """{"model":"rag-chat-model","messages":[{"role":"user","content":"안녕"}],"stream":false}"""
                )
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `entity appends JSON schema instructions for OpenClaw`() {
        val requestBody = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange ->
                requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
                val body = """{"choices":[{"message":{"role":"assistant","content":"{\"summary\":\"ok\"}"}}]}"""
                    .toByteArray()
                exchange.responseHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        try {
            contextRunner
                .withPropertyValues(
                    "openclaw.base-url=http://127.0.0.1:${server.address.port}",
                    "openclaw.api-key=test-key",
                    "openclaw.agent-id=agent-1"
                )
                .run { context ->
                    val model = context.getBean(OpenClawChatModel::class.java)
                    val chatClient = SpringAiRagConfig().chatClient(model)

                    assertThat(
                        chatClient.prompt()
                            .user("안녕")
                            .call()
                            .entity(NativeStructuredResponse::class.java)
                    ).isEqualTo(NativeStructuredResponse(summary = "ok"))
                }

            val json = jacksonObjectMapper().readTree(requestBody.get())
            assertThat(json.has("response_format")).isFalse()
            assertThat(json.at("/messages/0/content").asText())
                .contains("안녕", "JSON Schema", "summary", "string")
        } finally {
            server.stop(0)
        }
    }

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(OpenClawTestConfiguration::class.java)
        .withBean(RestClient.Builder::class.java, { RestClient.builder() })
        .withBean(
            RagProperties::class.java,
            { RagProperties(chat = RagProperties.Chat(model = "rag-chat-model")) }
        )
        .withBean(OpenClawChatModel::class.java)

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OpenClawProperties::class)
    private class OpenClawTestConfiguration

    private data class NativeStructuredResponse(
        val summary: String
    )
}
