package com.chamchamcham.batch.policy

import com.chamchamcham.application.policy.sync.PolicySyncService
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PolicySyncScheduler(
    private val policySyncService: PolicySyncService
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(
        cron = "\${policy.sync.nongup-ez.cron:0 20 3 * * *}",
        zone = "\${policy.sync.nongup-ez.zone:Asia/Seoul}"
    )
    fun syncNongupEzPolicies() {
        val result = policySyncService.runScheduledSync()
        log.info {
            "NongupEZ policy sync finished jobId=${result.jobId} status=${result.status} targetYear=${result.targetYear}"
        }
    }
}
