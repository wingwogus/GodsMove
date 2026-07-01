package com.godsmove.api.rag.client

import com.godsmove.application.coaching.rag.ChatMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenClawChatCompletionClientTest {
    @Test
    fun `complete sends OpenClaw agent header and parses content`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = OpenClawChatCompletionClient(
            restClientBuilder = builder,
            baseUrl = "http://openclaw.test",
            apiKey = "secret",
            agentId = "agri-rag-coach"
        )

        server.expect(requestTo("http://openclaw.test/v1/chat/completions"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
            .andExpect(header("x-openclaw-agent-id", "agri-rag-coach"))
            .andRespond(withSuccess("""{"choices":[{"message":{"content":"답변 [chunk:c1]"}}]}""", MediaType.APPLICATION_JSON))

        val answer = client.complete(listOf(ChatMessage("user", "질문")), "openclaw/agri-rag-coach")

        assertThat(answer).isEqualTo("답변 [chunk:c1]")
        server.verify()
    }
}
