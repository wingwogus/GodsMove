package com.chamchamcham.batch.policy

import com.chamchamcham.application.policy.sync.PolicySyncService
import com.chamchamcham.application.policy.sync.PolicySyncResult
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class PolicySyncSchedulerTest {
    @Test
    fun `scheduled sync delegates to application service`() {
        val service = mock(PolicySyncService::class.java)
        `when`(service.runScheduledSync()).thenReturn(
            PolicySyncResult.JobSummary(
                jobId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                status = PolicySyncJobStatus.SUCCEEDED,
                targetYear = "2026"
            )
        )
        val scheduler = PolicySyncScheduler(service)

        scheduler.syncNongupEzPolicies()

        verify(service).runScheduledSync()
    }
}
