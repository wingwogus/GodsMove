package com.chamchamcham.application.pesticide.sync

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PesticideSyncAsyncRunner(
    private val pesticideSyncService: PesticideSyncService
) {
    @Async
    fun run(jobId: UUID) {
        pesticideSyncService.runExistingJob(jobId)
    }
}
