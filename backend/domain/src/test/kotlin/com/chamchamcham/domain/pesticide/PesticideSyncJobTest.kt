package com.chamchamcham.domain.pesticide

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PesticideSyncJobTest {
    @Test
    fun `succeed records counters and finished timestamp`() {
        val job = PesticideSyncJob()

        job.succeed(totalCount = 143912, fetchedRowCount = 143912, createdApplicationCount = 140000)

        assertThat(job.status).isEqualTo(PesticideSyncJobStatus.SUCCEEDED)
        assertThat(job.totalCount).isEqualTo(143912)
        assertThat(job.fetchedRowCount).isEqualTo(143912)
        assertThat(job.createdApplicationCount).isEqualTo(140000)
        assertThat(job.errorMessage).isNull()
        assertThat(job.finishedAt).isNotNull()
    }

    @Test
    fun `fail records bounded error message`() {
        val job = PesticideSyncJob()

        job.fail("x".repeat(1200))

        assertThat(job.status).isEqualTo(PesticideSyncJobStatus.FAILED)
        assertThat(job.errorMessage).hasSize(1000)
        assertThat(job.finishedAt).isNotNull()
    }
}
