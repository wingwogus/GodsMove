package com.godsmove.api.dev.controller

import com.godsmove.application.coaching.rag.seed.DevRagSeedCommand
import com.godsmove.api.exception.GlobalExceptionHandler
import com.godsmove.api.security.CustomAccessDeniedHandler
import com.godsmove.api.security.CustomAuthenticationEntryPoint
import com.godsmove.api.security.JwtAuthenticationFilter
import com.godsmove.application.coaching.rag.seed.DevRagSeedResult
import com.godsmove.application.coaching.rag.seed.DevRagSeedService
import com.godsmove.application.security.TokenProvider
import com.godsmove.config.MDCLoggingFilter
import com.godsmove.config.SecurityConfig
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(DevRagSeedController::class)
@AutoConfigureMockMvc
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    CustomAccessDeniedHandler::class,
    CustomAuthenticationEntryPoint::class,
    JwtAuthenticationFilter::class,
    MDCLoggingFilter::class
)
@ActiveProfiles("local")
@TestPropertySource(properties = ["app.dev.rag-seed-key=test-seed-key"])
class DevRagSeedControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var devRagSeedService: DevRagSeedService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `seed returns generated token and indexed counts`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val farmId = UUID.fromString("10000000-0000-0000-0000-000000000042")
        val cropId = UUID.fromString("20000000-0000-0000-0000-000000000042")
        val command = DevRagSeedCommand(
            pdfPath = "/tmp/guide.pdf",
            resetIndex = true,
            includePdf = true,
            includeFarmingRecords = true,
            maxPdfChunks = 12
        )
        `when`(devRagSeedService.seed(command))
            .thenReturn(
                DevRagSeedResult(
                    memberId = memberId,
                    farmId = farmId,
                    cropId = cropId,
                    workTypeIds = mapOf("irrigation" to UUID.fromString("30000000-0000-0000-0000-000000000002")),
                    recordIds = listOf(UUID.fromString("40000000-0000-0000-0000-000000000002")),
                    pdfChunksIndexed = 12,
                    farmingRecordChunksIndexed = 5,
                    embeddingModel = "bge-m3"
                )
            )
        `when`(tokenProvider.createAccessToken(memberId, "ROLE_USER"))
            .thenReturn("access-token")

        mockMvc.perform(
            post("/api/v1/dev/rag/seed")
                .header(DEV_SEED_KEY_HEADER, DEV_SEED_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pdfPath":"/tmp/guide.pdf","maxPdfChunks":12}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
            .andExpect(jsonPath("$.data.memberId", equalTo(memberId.toString())))
            .andExpect(jsonPath("$.data.pdfChunksIndexed", equalTo(12)))
            .andExpect(jsonPath("$.data.farmingRecordChunksIndexed", equalTo(5)))
    }

    @Test
    fun `seed rejects too many pdf chunks`() {
        mockMvc.perform(
            post("/api/v1/dev/rag/seed")
                .header(DEV_SEED_KEY_HEADER, DEV_SEED_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pdfPath":"/tmp/guide.pdf","maxPdfChunks":1001}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `seed rejects missing dev seed key`() {
        mockMvc.perform(
            post("/api/v1/dev/rag/seed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pdfPath":"/tmp/guide.pdf","maxPdfChunks":12}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_002")))
    }

    @Test
    fun `seed allows localhost frontend preflight in local profile`() {
        mockMvc.perform(
            options("/api/v1/dev/rag/seed")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
    }

    private companion object {
        const val DEV_SEED_KEY_HEADER = "X-GodsMove-Dev-Seed-Key"
        const val DEV_SEED_KEY = "test-seed-key"
    }
}
