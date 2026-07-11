package com.chamchamcham.api.policy.dto.sync

import com.chamchamcham.application.policy.sync.PolicySyncResult
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.chamchamcham.domain.policy.PolicySyncTriggerType
import java.time.LocalDateTime
import java.util.UUID

data class PolicySyncJobSummaryResponse(
    val jobId: UUID,
    val status: PolicySyncJobStatus,
    val targetYear: String
) {
    companion object {
        fun from(result: PolicySyncResult.JobSummary): PolicySyncJobSummaryResponse =
            PolicySyncJobSummaryResponse(
                jobId = result.jobId,
                status = result.status,
                targetYear = result.targetYear
            )
    }
}

data class PolicySyncJobDetailResponse(
    val jobId: UUID,
    val source: PolicySource,
    val targetYear: String,
    val triggerType: PolicySyncTriggerType,
    val status: PolicySyncJobStatus,
    val totalCount: Int,
    val syncedCount: Int,
    val detailSuccessCount: Int,
    val detailFailureCount: Int,
    val errorMessage: String?,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?
) {
    companion object {
        fun from(result: PolicySyncResult.JobDetail): PolicySyncJobDetailResponse =
            PolicySyncJobDetailResponse(
                jobId = result.jobId,
                source = result.source,
                targetYear = result.targetYear,
                triggerType = result.triggerType,
                status = result.status,
                totalCount = result.totalCount,
                syncedCount = result.syncedCount,
                detailSuccessCount = result.detailSuccessCount,
                detailFailureCount = result.detailFailureCount,
                errorMessage = result.errorMessage,
                startedAt = result.startedAt,
                finishedAt = result.finishedAt
            )
    }
}
