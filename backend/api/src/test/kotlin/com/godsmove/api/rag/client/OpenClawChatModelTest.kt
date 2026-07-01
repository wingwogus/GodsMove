package com.godsmove.api.rag.client

import com.godsmove.config.OpenClawProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenClawChatModelTest {
    @Test
    fun `call sends auth and agent headers to OpenClaw`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val model = OpenClawChatModel(
            restClientBuilder = builder,
            properties = OpenClawProperties(
                baseUrl = "http://openclaw.test",
                apiKey = "test-key",
                agentId = "agent-1",
                model = "openclaw/agri-rag-coach"
            )
        )

        server.expect(requestTo("http://openclaw.test/v1/chat/completions"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(header("x-openclaw-agent-id", "agent-1"))
            .andExpect(content().json("""{"model":"openclaw/agri-rag-coach","messages":[{"role":"user","content":"안녕"}],"stream":false}"""))
            .andRespond(withSuccess("""{"choices":[{"message":{"role":"assistant","content":"{\"summary\":\"ok\"}"}}]}""", MediaType.APPLICATION_JSON))

        val response = model.call(Prompt(UserMessage("안녕")))

        assertThat(response.result.output.text).contains("summary")
        server.verify()
    }
}
