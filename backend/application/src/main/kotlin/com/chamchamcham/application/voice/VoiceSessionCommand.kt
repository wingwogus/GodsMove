package com.chamchamcham.application.voice

import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.domain.voice.VoiceTurnRole
import java.util.UUID

object VoiceSessionCommand {
    data class Create(val memberId: UUID)

    data class SubmitTurns(
        val memberId: UUID,
        val sessionId: UUID,
        val turns: List<TurnInput>,
        val candidate: VoiceRecordCandidate,
    )

    data class TurnInput(
        val role: VoiceTurnRole,
        val content: String,
        val extractedFields: String? = null,
    )

    data class Confirm(
        val memberId: UUID,
        val sessionId: UUID,
        val record: FarmingRecordCommand.Create,
    )

    data class Cancel(
        val memberId: UUID,
        val sessionId: UUID,
    )
}
