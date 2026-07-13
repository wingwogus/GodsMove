package com.chamchamcham.api.pesticide.dto

import com.chamchamcham.application.pesticide.PesticideResult
import com.chamchamcham.application.pesticide.sync.PesticideProbeResult
import com.chamchamcham.application.pesticide.sync.PsisPesticideRow
import java.util.UUID

object PesticideResponses {
    data class PesticideSummaryResponse(
        val id: UUID,
        val itemName: String,
        val brandName: String,
        val usageCategory: String?,
        val manufacturer: String?,
    ) {
        companion object {
            fun from(result: PesticideResult.Summary): PesticideSummaryResponse = PesticideSummaryResponse(
                id = result.id,
                itemName = result.itemName,
                brandName = result.brandName,
                usageCategory = result.usageCategory,
                manufacturer = result.manufacturer,
            )
        }
    }

    data class PesticidePageResponse(
        val items: List<PesticideSummaryResponse>,
        val nextCursor: String?,
    ) {
        companion object {
            fun from(result: PesticideResult.Page): PesticidePageResponse = PesticidePageResponse(
                items = result.items.map(PesticideSummaryResponse::from),
                nextCursor = result.nextCursor,
            )
        }
    }

    data class PestSummaryResponse(
        val id: UUID,
        val name: String,
    ) {
        companion object {
            fun from(result: PesticideResult.PestSummary): PestSummaryResponse = PestSummaryResponse(
                id = result.id,
                name = result.name,
            )
        }
    }

    data class PesticideProbeResponse(
        val resultCode: String?,
        val resultMsg: String?,
        val totalCount: Int?,
        val itemCount: Int,
        val distinctTagNames: List<String>,
        val sampleRawItem: Map<String, String>?,
        val requiredKeyResolution: Map<String, Boolean>,
        val mapped: PsisPesticideRow?,
    ) {
        companion object {
            fun from(result: PesticideProbeResult): PesticideProbeResponse = PesticideProbeResponse(
                resultCode = result.resultCode,
                resultMsg = result.resultMsg,
                totalCount = result.totalCount,
                itemCount = result.itemCount,
                distinctTagNames = result.distinctTagNames,
                sampleRawItem = result.sampleRawItem,
                requiredKeyResolution = result.requiredKeyResolution,
                mapped = result.mapped,
            )
        }
    }
}
