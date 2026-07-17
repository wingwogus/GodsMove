package com.chamchamcham.api.voice.dto

import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.voice.VoiceRecordCandidate
import com.chamchamcham.application.voice.VoiceSessionResult
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.voice.VoiceSessionStatus
import java.time.LocalDateTime
import java.util.UUID

object VoiceSessionResponses {
    data class CreatedResponse(
        val sessionId: UUID,
        val clientSecret: String,
        val expiresAt: LocalDateTime,
        val model: String,
        val farms: List<FarmOptionResponse>,
        val cropsByFarm: Map<String, List<CropOptionResponse>>,
        val maxRounds: Int,
        val maxDurationSeconds: Int,
    ) {
        companion object {
            fun from(result: VoiceSessionResult.Created): CreatedResponse = CreatedResponse(
                sessionId = result.sessionId,
                clientSecret = result.clientSecret,
                expiresAt = result.expiresAt,
                model = result.model,
                farms = result.farms.map { FarmOptionResponse(it.farmId, it.name) },
                cropsByFarm = result.cropsByFarm.mapValues { (_, crops) -> crops.map { CropOptionResponse(it.cropId, it.name) } },
                maxRounds = result.maxRounds,
                maxDurationSeconds = result.maxDurationSeconds,
            )
        }
    }

    data class FarmOptionResponse(val farmId: UUID, val name: String)

    data class CropOptionResponse(val cropId: UUID, val name: String)

    data class ProcessedResponse(
        val sessionId: UUID,
        val status: VoiceSessionStatus,
        val candidate: CandidateResponse,
        val missingFields: List<String>,
    ) {
        companion object {
            fun from(result: VoiceSessionResult.Processed): ProcessedResponse = ProcessedResponse(
                sessionId = result.sessionId,
                status = result.status,
                candidate = CandidateResponse.from(result.candidate),
                missingFields = result.missingFields,
            )
        }
    }

    data class CandidateResponse(
        val farmId: UUID?,
        val cropId: UUID?,
        val workType: WorkType?,
        val workedAt: LocalDateTime?,
        val weatherCondition: String?,
        val weatherTemperature: Int?,
        val memo: String?,
        val planting: FarmingRecordCommand.PlantingDetail?,
        val watering: FarmingRecordCommand.WateringDetail?,
        val fertilizing: FarmingRecordCommand.FertilizingDetail?,
        val pestControl: FarmingRecordCommand.PestControlDetail?,
        val weeding: FarmingRecordCommand.WeedingDetail?,
        val harvest: FarmingRecordCommand.HarvestDetail?,
    ) {
        companion object {
            fun from(candidate: VoiceRecordCandidate): CandidateResponse = CandidateResponse(
                farmId = candidate.farmId,
                cropId = candidate.cropId,
                workType = candidate.workType,
                workedAt = candidate.workedAt,
                weatherCondition = candidate.weatherCondition,
                weatherTemperature = candidate.weatherTemperature,
                memo = candidate.memo,
                planting = candidate.planting,
                watering = candidate.watering,
                fertilizing = candidate.fertilizing,
                pestControl = candidate.pestControl,
                weeding = candidate.weeding,
                harvest = candidate.harvest,
            )
        }
    }

    data class ConfirmedResponse(
        val sessionId: UUID,
        val recordId: UUID,
        val workType: WorkType,
    ) {
        companion object {
            fun from(result: VoiceSessionResult.Confirmed): ConfirmedResponse =
                ConfirmedResponse(sessionId = result.sessionId, recordId = result.recordId, workType = result.workType)
        }
    }

    data class CancelledResponse(
        val sessionId: UUID,
        val status: VoiceSessionStatus,
    ) {
        companion object {
            fun from(result: VoiceSessionResult.Cancelled): CancelledResponse =
                CancelledResponse(sessionId = result.sessionId, status = result.status)
        }
    }
}
