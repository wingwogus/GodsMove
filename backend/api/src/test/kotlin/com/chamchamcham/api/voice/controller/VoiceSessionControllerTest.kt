package com.chamchamcham.api.voice.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.application.voice.FarmOption
import com.chamchamcham.application.voice.VoiceRecordCandidate
import com.chamchamcham.application.voice.VoiceSessionCommand
import com.chamchamcham.application.voice.VoiceSessionResult
import com.chamchamcham.application.voice.VoiceSessionService
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.voice.VoiceSessionStatus
import com.chamchamcham.domain.voice.VoiceTurnRole
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(VoiceSessionController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class VoiceSessionControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val sessionId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000401")

    @MockBean private lateinit var voiceSessionService: VoiceSessionService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `create session returns ephemeral secret and farm options`() {
        `when`(voiceSessionService.create(VoiceSessionCommand.Create(memberId = memberId)))
            .thenReturn(
                VoiceSessionResult.Created(
                    sessionId = sessionId,
                    clientSecret = "secret",
                    expiresAt = LocalDateTime.of(2026, 6, 1, 9, 10),
                    model = "gpt-realtime",
                    farms = listOf(FarmOption(farmId, "약초농장")),
                    cropsByFarm = mapOf(farmId.toString() to emptyList()),
                )
            )

        mockMvc.perform(post("/api/v1/voice-sessions").with(authenticatedMember(memberId.toString())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sessionId", equalTo(sessionId.toString())))
            .andExpect(jsonPath("$.data.clientSecret", equalTo("secret")))
    }

    @Test
    fun `create session without auth returns unauthorized`() {
        mockMvc.perform(post("/api/v1/voice-sessions"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    @Test
    fun `submit turns maps request and returns missing fields`() {
        `when`(
            voiceSessionService.submitTurns(
                VoiceSessionCommand.SubmitTurns(
                    memberId = memberId,
                    sessionId = sessionId,
                    turns = listOf(VoiceSessionCommand.TurnInput(role = VoiceTurnRole.USER, content = "물 줬어요")),
                    candidate = VoiceRecordCandidate(workType = WorkType.WATERING),
                )
            )
        ).thenReturn(
            VoiceSessionResult.Processed(
                sessionId = sessionId,
                status = VoiceSessionStatus.WAITING_CONFIRMATION,
                candidate = VoiceRecordCandidate(workType = WorkType.WATERING),
                missingFields = listOf("farmId", "cropId"),
            )
        )

        mockMvc.perform(
            patch("/api/v1/voice-sessions/$sessionId/turns")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "turns":[{"role":"USER","content":"물 줬어요"}],
                      "candidate":{"workType":"WATERING"}
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status", equalTo("WAITING_CONFIRMATION")))
            .andExpect(jsonPath("$.data.missingFields[0]", equalTo("farmId")))
    }

    @Test
    fun `confirm forces entryMode to VOICE regardless of request body`() {
        `when`(voiceSessionService.confirm(expectedConfirmCommand()))
            .thenReturn(VoiceSessionResult.Confirmed(sessionId = sessionId, recordId = recordId, workType = WorkType.WATERING))

        mockMvc.perform(
            post("/api/v1/voice-sessions/$sessionId/confirm")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.recordId", equalTo(recordId.toString())))
    }

    @Test
    fun `cancel returns cancelled status`() {
        `when`(voiceSessionService.cancel(VoiceSessionCommand.Cancel(memberId = memberId, sessionId = sessionId)))
            .thenReturn(VoiceSessionResult.Cancelled(sessionId = sessionId, status = VoiceSessionStatus.CANCELLED))

        mockMvc.perform(post("/api/v1/voice-sessions/$sessionId/cancel").with(authenticatedMember(memberId.toString())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status", equalTo("CANCELLED")))
    }

    private fun expectedConfirmCommand() = VoiceSessionCommand.Confirm(
        memberId = memberId,
        sessionId = sessionId,
        record = FarmingRecordCommand.Create(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.WATERING,
            workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "물을 충분히 주었고 토양 상태도 양호하여 특별한 이상 없이 관수 작업을 마쳤습니다",
            entryMode = EntryMode.VOICE,
        ),
    )

    private fun confirmJson(): String = """
        {
          "farmId":"$farmId",
          "cropId":"$cropId",
          "workType":"WATERING",
          "workedAt":"2026-06-01T09:00:00",
          "weatherCondition":"맑음",
          "weatherTemperature":20,
          "memo":"물을 충분히 주었고 토양 상태도 양호하여 특별한 이상 없이 관수 작업을 마쳤습니다",
          "entryMode":"MANUAL"
        }
    """.trimIndent()

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }
    }
}
