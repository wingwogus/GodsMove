package com.chamchamcham.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PolicySyncJobTest {
    @Test
    fun `succeed records counters and finished timestamp`() {
        val job = PolicySyncJob(
            source = PolicySource.NONGUP_EZ,
            targetYear = "2026",
            triggerType = PolicySyncTriggerType.SCHEDULED,
            createdByMemberId = null
        )

        job.succeed(totalCount = 372, syncedCount = 372, detailSuccessCount = 360, detailFailureCount = 12)

        assertThat(job.status).isEqualTo(PolicySyncJobStatus.SUCCEEDED)
        assertThat(job.totalCount).isEqualTo(372)
        assertThat(job.detailFailureCount).isEqualTo(12)
        assertThat(job.finishedAt).isNotNull()
    }

    @Test
    fun `fail records bounded error message`() {
        val job = PolicySyncJob(
            source = PolicySource.NONGUP_EZ,
            targetYear = "2026",
            triggerType = PolicySyncTriggerType.ADMIN,
            createdByMemberId = null
        )

        job.fail("x".repeat(1200))

        assertThat(job.status).isEqualTo(PolicySyncJobStatus.FAILED)
        assertThat(job.errorMessage).hasSize(1000)
        assertThat(job.finishedAt).isNotNull()
    }
}
