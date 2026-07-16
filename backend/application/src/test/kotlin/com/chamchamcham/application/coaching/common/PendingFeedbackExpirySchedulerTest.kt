package com.chamchamcham.application.coaching.common

import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.config.AsyncConfig
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

class PendingFeedbackExpirySchedulerTest {
    @Test
    fun `processing timeout is a stable record and report failure code`() {
        assertThat(RecordFeedbackFailureCode.PROCESSING_TIMEOUT.name)
            .isEqualTo("PROCESSING_TIMEOUT")
        assertThat(ReportFeedbackFailureCode.PROCESSING_TIMEOUT.name)
            .isEqualTo("PROCESSING_TIMEOUT")
    }

    @Test
    fun `pending expiry runs every configured interval in one transaction`() {
        assertThat(AsyncConfig::class.java.isAnnotationPresent(EnableScheduling::class.java)).isTrue()

        val expirePending = PendingFeedbackExpiryScheduler::class.java
            .getDeclaredMethod("expirePending")

        assertThat(expirePending.getAnnotation(Scheduled::class.java).fixedDelayString)
            .isEqualTo("\${rag.execution.scan-interval-millis:60000}")
        assertThat(expirePending.isAnnotationPresent(Transactional::class.java)).isTrue()
    }

    @Test
    fun `pending expiry applies configured cutoff to both feedback types`() {
        val recordRepository = Mockito.mock(RecordFeedbackRepository::class.java)
        val reportRepository = Mockito.mock(ReportFeedbackRepository::class.java)
        val scheduler = PendingFeedbackExpiryScheduler(
            recordRepository,
            reportRepository,
            RagProperties(),
        )

        scheduler.expirePending()

        assertExpiryInvocation(recordRepository)
        assertExpiryInvocation(reportRepository)
    }

    private fun assertExpiryInvocation(repository: Any) {
        val invocation = Mockito.mockingDetails(repository).invocations
            .singleOrNull { it.method.name == "failPendingUpdatedBefore" }
        assertThat(invocation).isNotNull
        val arguments = requireNotNull(invocation).arguments
        val cutoff = arguments[0] as LocalDateTime
        val failedAt = arguments[1] as LocalDateTime

        assertThat(cutoff).isEqualTo(failedAt.minusSeconds(240))
        assertThat(arguments[2]).isEqualTo("PROCESSING_TIMEOUT")
    }
}
