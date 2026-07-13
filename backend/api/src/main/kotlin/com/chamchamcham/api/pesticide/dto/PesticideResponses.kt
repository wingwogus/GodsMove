package com.chamchamcham.api.pesticide.dto

import com.chamchamcham.application.pesticide.PesticideResult
import com.chamchamcham.application.pesticide.sync.PesticideProbeResult
import com.chamchamcham.application.pesticide.sync.PesticideSyncResult
import com.chamchamcham.application.pesticide.sync.PsisPesticideRow
import com.chamchamcham.domain.pesticide.PesticideSyncJobStatus
import java.time.LocalDateTime
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
        val errorCode: String?,
        val errorMsg: String?,
        val totalCount: Int?,
        val itemCount: Int,
        val distinctTagNames: List<String>,
        val sampleRawItem: Map<String, String>?,
        val requiredKeyResolution: Map<String, Boolean>,
        val mapped: PsisPesticideRow?,
    ) {
        companion object {
            fun from(result: PesticideProbeResult): PesticideProbeResponse = PesticideProbeResponse(
                errorCode = result.errorCode,
                errorMsg = result.errorMsg,
                totalCount = result.totalCount,
                itemCount = result.itemCount,
                distinctTagNames = result.distinctTagNames,
                sampleRawItem = result.sampleRawItem,
                requiredKeyResolution = result.requiredKeyResolution,
                mapped = result.mapped,
            )
        }
    }

    data class PesticideSyncJobSummaryResponse(
        val jobId: UUID,
        val status: PesticideSyncJobStatus,
    ) {
        companion object {
            fun from(result: PesticideSyncResult.JobSummary): PesticideSyncJobSummaryResponse =
                PesticideSyncJobSummaryResponse(
                    jobId = result.jobId,
                    status = result.status,
                )
        }
    }

    data class PesticideSyncJobDetailResponse(
        val jobId: UUID,
        val status: PesticideSyncJobStatus,
        val totalCount: Int,
        val fetchedRowCount: Int,
        val createdApplicationCount: Int,
        val errorMessage: String?,
        val startedAt: LocalDateTime,
        val finishedAt: LocalDateTime?,
    ) {
        companion object {
            fun from(result: PesticideSyncResult.JobDetail): PesticideSyncJobDetailResponse =
                PesticideSyncJobDetailResponse(
                    jobId = result.jobId,
                    status = result.status,
                    totalCount = result.totalCount,
                    fetchedRowCount = result.fetchedRowCount,
                    createdApplicationCount = result.createdApplicationCount,
                    errorMessage = result.errorMessage,
                    startedAt = result.startedAt,
                    finishedAt = result.finishedAt,
                )
        }
    }
}
