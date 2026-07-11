package com.chamchamcham.application.policy.sync

import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJob
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.chamchamcham.domain.policy.PolicySyncTriggerType
import java.time.LocalDateTime
import java.util.UUID

object PolicySyncResult {
    data class JobSummary(
        val jobId: UUID,
        val status: PolicySyncJobStatus,
        val targetYear: String
    ) {
        companion object {
            fun from(job: PolicySyncJob): JobSummary =
                JobSummary(requireNotNull(job.id), job.status, job.targetYear)
        }
    }

    data class JobDetail(
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
            fun from(job: PolicySyncJob): JobDetail =
                JobDetail(
                    jobId = requireNotNull(job.id),
                    source = job.source,
                    targetYear = job.targetYear,
                    triggerType = job.triggerType,
                    status = job.status,
                    totalCount = job.totalCount,
                    syncedCount = job.syncedCount,
                    detailSuccessCount = job.detailSuccessCount,
                    detailFailureCount = job.detailFailureCount,
                    errorMessage = job.errorMessage,
                    startedAt = job.startedAt,
                    finishedAt = job.finishedAt
                )
        }
    }
}
