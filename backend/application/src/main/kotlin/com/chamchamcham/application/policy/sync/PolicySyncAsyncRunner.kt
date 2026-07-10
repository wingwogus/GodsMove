package com.chamchamcham.application.policy.sync

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PolicySyncAsyncRunner(
    private val policySyncService: PolicySyncService
) {
    @Async
    fun run(jobId: UUID) {
        policySyncService.runExistingJob(jobId)
    }
}
