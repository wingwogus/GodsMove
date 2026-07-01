package com.godsmove.api.rag.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OllamaEmbeddingClientTest {
    @Test
    fun `embed parses Ollama embeddings response`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = OllamaEmbeddingClient(builder, "http://ollama.test")

        server.expect(requestTo("http://ollama.test/api/embed"))
            .andExpect(content().json("""{"model":"bge-m3","input":"관수"}"""))
            .andRespond(withSuccess("""{"embeddings":[[0.1,0.2,0.3]]}""", MediaType.APPLICATION_JSON))

        assertThat(client.embed("관수", "bge-m3")).containsExactly(0.1, 0.2, 0.3)
        server.verify()
    }
}
