package com.godsmove.api.dev.dto

import com.godsmove.application.coaching.rag.seed.DevRagSeedResult
import java.util.UUID

object DevRagSeedResponses {
    data class SeedResponse(
        val accessToken: String,
        val memberId: UUID,
        val farmId: UUID,
        val cropId: UUID,
        val workTypeIds: Map<String, UUID>,
        val recordIds: List<UUID>,
        val pdfChunksIndexed: Int,
        val farmingRecordChunksIndexed: Int,
        val embeddingModel: String
    ) {
        companion object {
            fun from(result: DevRagSeedResult, accessToken: String): SeedResponse {
                return SeedResponse(
                    accessToken = accessToken,
                    memberId = result.memberId,
                    farmId = result.farmId,
                    cropId = result.cropId,
                    workTypeIds = result.workTypeIds,
                    recordIds = result.recordIds,
                    pdfChunksIndexed = result.pdfChunksIndexed,
                    farmingRecordChunksIndexed = result.farmingRecordChunksIndexed,
                    embeddingModel = result.embeddingModel
                )
            }
        }
    }
}
