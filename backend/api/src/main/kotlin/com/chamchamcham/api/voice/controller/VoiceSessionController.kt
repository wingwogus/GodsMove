package com.chamchamcham.api.voice.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.farming.dto.FarmingRecordRequests
import com.chamchamcham.api.farming.dto.toCreateCommand
import com.chamchamcham.api.voice.dto.VoiceSessionRequests
import com.chamchamcham.api.voice.dto.VoiceSessionResponses
import com.chamchamcham.api.voice.dto.toCommand
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.voice.VoiceSessionCommand
import com.chamchamcham.application.voice.VoiceSessionService
import com.chamchamcham.domain.farming.EntryMode
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/voice-sessions")
class VoiceSessionController(
    private val voiceSessionService: VoiceSessionService
) {
    @PostMapping
    fun createSession(
        @AuthenticationPrincipal memberId: String?
    ): ResponseEntity<ApiResponse<VoiceSessionResponses.CreatedResponse>> {
        val result = voiceSessionService.create(VoiceSessionCommand.Create(memberId = parseMemberId(memberId)))
        return ResponseEntity.ok(ApiResponse.ok(VoiceSessionResponses.CreatedResponse.from(result)))
    }

    @PatchMapping("/{sessionId}/turns")
    fun submitTurns(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: VoiceSessionRequests.SubmitTurnsRequest
    ): ResponseEntity<ApiResponse<VoiceSessionResponses.ProcessedResponse>> {
        val result = voiceSessionService.submitTurns(request.toCommand(parseMemberId(memberId), sessionId))
        return ResponseEntity.ok(ApiResponse.ok(VoiceSessionResponses.ProcessedResponse.from(result)))
    }

    @PostMapping("/{sessionId}/confirm")
    fun confirm(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: FarmingRecordRequests.SaveRecordRequest
    ): ResponseEntity<ApiResponse<VoiceSessionResponses.ConfirmedResponse>> {
        val parsedMemberId = parseMemberId(memberId)
        val command = VoiceSessionCommand.Confirm(
            memberId = parsedMemberId,
            sessionId = sessionId,
            record = request.toCreateCommand(parsedMemberId, entryMode = EntryMode.VOICE),
        )
        val result = voiceSessionService.confirm(command)
        return ResponseEntity.ok(ApiResponse.ok(VoiceSessionResponses.ConfirmedResponse.from(result)))
    }

    @PostMapping("/{sessionId}/cancel")
    fun cancel(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable sessionId: UUID
    ): ResponseEntity<ApiResponse<VoiceSessionResponses.CancelledResponse>> {
        val result = voiceSessionService.cancel(
            VoiceSessionCommand.Cancel(memberId = parseMemberId(memberId), sessionId = sessionId)
        )
        return ResponseEntity.ok(ApiResponse.ok(VoiceSessionResponses.CancelledResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
