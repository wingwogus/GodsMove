package com.chamchamcham.application.pesticide.sync

import com.chamchamcham.domain.pesticide.PesticideSyncJob
import com.chamchamcham.domain.pesticide.PesticideSyncJobStatus
import java.time.LocalDateTime
import java.util.UUID

object PesticideSyncResult {
    data class JobSummary(
        val jobId: UUID,
        val status: PesticideSyncJobStatus
    ) {
        companion object {
            fun from(job: PesticideSyncJob): JobSummary =
                JobSummary(requireNotNull(job.id), job.status)
        }
    }

    data class JobDetail(
        val jobId: UUID,
        val status: PesticideSyncJobStatus,
        val totalCount: Int,
        val fetchedRowCount: Int,
        val createdApplicationCount: Int,
        val errorMessage: String?,
        val startedAt: LocalDateTime,
        val finishedAt: LocalDateTime?
    ) {
        companion object {
            fun from(job: PesticideSyncJob): JobDetail =
                JobDetail(
                    jobId = requireNotNull(job.id),
                    status = job.status,
                    totalCount = job.totalCount,
                    fetchedRowCount = job.fetchedRowCount,
                    createdApplicationCount = job.createdApplicationCount,
                    errorMessage = job.errorMessage,
                    startedAt = job.startedAt,
                    finishedAt = job.finishedAt
                )
        }
    }
}
