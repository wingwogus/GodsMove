package com.godsmove.api.dev.dto

import com.godsmove.application.coaching.rag.seed.DevRagSeedCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

object DevRagSeedRequests {
    data class SeedRequest(
        val pdfPath: String? = null,
        val resetIndex: Boolean = true,
        val includePdf: Boolean = true,
        val includeFarmingRecords: Boolean = true,

        @field:Min(value = 1, message = "maxPdfChunks는 1 이상이어야 합니다")
        @field:Max(value = 1000, message = "maxPdfChunks는 1000 이하여야 합니다")
        val maxPdfChunks: Int = 300
    ) {
        fun toCommand(): DevRagSeedCommand {
            return DevRagSeedCommand(
                pdfPath = pdfPath,
                resetIndex = resetIndex,
                includePdf = includePdf,
                includeFarmingRecords = includeFarmingRecords,
                maxPdfChunks = maxPdfChunks
            )
        }
    }
}
