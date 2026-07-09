package com.chamchamcham.application.voice

import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.domain.farming.WorkType
import java.time.LocalDateTime
import java.util.UUID

/**
 * AI가 음성 대화에서 추출한 후보값. BR-AI-002/003/004에 따라 확신하지 못하는 값은
 * null(미확정)로 두며, 이 상태로는 FarmingRecord를 생성하지 않는다(BR-VOICE-003).
 */
data class VoiceRecordCandidate(
    val farmId: UUID? = null,
    val cropId: UUID? = null,
    val workType: WorkType? = null,
    val workedAt: LocalDateTime? = null,
    val weatherCondition: String? = null,
    val weatherTemperature: Int? = null,
    val memo: String? = null,
    val planting: FarmingRecordCommand.PlantingDetail? = null,
    val watering: FarmingRecordCommand.WateringDetail? = null,
    val fertilizing: FarmingRecordCommand.FertilizingDetail? = null,
    val pestControl: FarmingRecordCommand.PestControlDetail? = null,
    val weeding: FarmingRecordCommand.WeedingDetail? = null,
    val harvest: FarmingRecordCommand.HarvestDetail? = null,
)
