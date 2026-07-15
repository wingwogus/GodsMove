package com.chamchamcham.api.coaching.chat.dto

import com.chamchamcham.application.coaching.chat.CoachingRagCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate
import java.util.UUID

object CoachingRagRequests {
    data class QueryRequest(
        @field:NotBlank(message = "질문을 입력해주세요")
        val question: String,
        val farmId: UUID? = null,
        val cropId: UUID? = null,
        val recordId: UUID? = null,
        val periodStart: LocalDate? = null,
        val periodEnd: LocalDate? = null,
        @field:Min(value = 1, message = "topK는 1 이상이어야 합니다")
        @field:Max(value = 20, message = "topK는 20 이하여야 합니다")
        val topK: Int? = null
    ) {
        fun toCommand(memberId: UUID): CoachingRagCommand {
            return CoachingRagCommand(
                memberId = memberId,
                question = question,
                farmId = farmId,
                cropId = cropId,
                recordId = recordId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                topK = topK
            )
        }
    }
}
