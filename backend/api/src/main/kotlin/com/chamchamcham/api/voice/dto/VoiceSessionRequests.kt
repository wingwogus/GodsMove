package com.chamchamcham.api.voice.dto

import com.chamchamcham.api.farming.dto.FarmingRecordRequests
import com.chamchamcham.api.farming.dto.toCommand
import com.chamchamcham.application.voice.VoiceRecordCandidate
import com.chamchamcham.application.voice.VoiceSessionCommand
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.voice.VoiceTurnRole
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

object VoiceSessionRequests {
    data class SubmitTurnsRequest(
        // 40 = 라운드당 2턴 × app.voice-session.max-rounds(10) × 여유 2배(인사·마무리 포함)
        @field:NotEmpty(message = "대화 턴이 비어있습니다")
        @field:Size(max = 40, message = "대화 턴은 최대 40개까지 제출할 수 있습니다")
        val turns: List<TurnRequest>,

        @field:Valid
        val candidate: CandidateRequest,
    )

    data class TurnRequest(
        val role: VoiceTurnRole,

        @field:NotBlank(message = "발화 내용을 입력해주세요")
        @field:Size(max = 4000, message = "발화 내용은 4000자 이내여야 합니다")
        val content: String,

        val extractedFields: String? = null,
    )

    data class CandidateRequest(
        val farmId: UUID? = null,
        val cropId: UUID? = null,
        val workType: WorkType? = null,
        val workedAt: LocalDateTime? = null,
        val weatherCondition: String? = null,
        val weatherTemperature: Int? = null,
        val memo: String? = null,

        @field:Valid
        val planting: FarmingRecordRequests.PlantingDetailRequest? = null,

        @field:Valid
        val watering: FarmingRecordRequests.WateringDetailRequest? = null,

        @field:Valid
        val fertilizing: FarmingRecordRequests.FertilizingDetailRequest? = null,

        @field:Valid
        val pestControl: FarmingRecordRequests.PestControlDetailRequest? = null,

        @field:Valid
        val weeding: FarmingRecordRequests.WeedingDetailRequest? = null,

        @field:Valid
        val harvest: FarmingRecordRequests.HarvestDetailRequest? = null,
    )
}

fun VoiceSessionRequests.SubmitTurnsRequest.toCommand(memberId: UUID, sessionId: UUID): VoiceSessionCommand.SubmitTurns =
    VoiceSessionCommand.SubmitTurns(
        memberId = memberId,
        sessionId = sessionId,
        turns = turns.map { VoiceSessionCommand.TurnInput(role = it.role, content = it.content, extractedFields = it.extractedFields) },
        candidate = candidate.toCandidate(),
    )

fun VoiceSessionRequests.CandidateRequest.toCandidate(): VoiceRecordCandidate =
    VoiceRecordCandidate(
        farmId = farmId,
        cropId = cropId,
        workType = workType,
        workedAt = workedAt,
        weatherCondition = weatherCondition,
        weatherTemperature = weatherTemperature,
        memo = memo,
        planting = planting?.toCommand(),
        watering = watering?.toCommand(),
        fertilizing = fertilizing?.toCommand(),
        pestControl = pestControl?.toCommand(),
        weeding = weeding?.toCommand(),
        harvest = harvest?.toCommand(),
    )
