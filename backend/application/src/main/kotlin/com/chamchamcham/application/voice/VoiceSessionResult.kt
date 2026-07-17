package com.chamchamcham.application.voice

import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.voice.VoiceSessionStatus
import java.time.LocalDateTime
import java.util.UUID

object VoiceSessionResult {
    data class Created(
        val sessionId: UUID,
        val clientSecret: String,
        val expiresAt: LocalDateTime,
        val model: String,
        val farms: List<FarmOption>,
        val cropsByFarm: Map<String, List<CropOption>>,
        val maxRounds: Int,
        val maxDurationSeconds: Int,
    )

    data class Processed(
        val sessionId: UUID,
        val status: VoiceSessionStatus,
        val candidate: VoiceRecordCandidate,
        val missingFields: List<String>,
    )

    data class Confirmed(
        val sessionId: UUID,
        val recordId: UUID,
        val workType: WorkType,
    )

    data class Cancelled(
        val sessionId: UUID,
        val status: VoiceSessionStatus,
    )
}
