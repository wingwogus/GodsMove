package com.chamchamcham.application.coaching.common

import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Component
class PendingFeedbackExpiryScheduler(
    private val recordFeedbackRepository: RecordFeedbackRepository,
    private val reportFeedbackRepository: ReportFeedbackRepository,
    private val ragProperties: RagProperties,
) {
    @Scheduled(fixedDelayString = "\${rag.execution.scan-interval-millis:60000}")
    @Transactional
    fun expirePending() {
        val failedAt = LocalDateTime.now()
        val cutoff = failedAt.minus(Duration.ofMillis(ragProperties.execution.pendingTimeoutMillis))
        val expiredRecordCount = recordFeedbackRepository.failPendingUpdatedBefore(
            cutoff,
            failedAt,
            RecordFeedbackFailureCode.PROCESSING_TIMEOUT.name,
        )
        val expiredReportCount = reportFeedbackRepository.failPendingUpdatedBefore(
            cutoff,
            failedAt,
            ReportFeedbackFailureCode.PROCESSING_TIMEOUT.name,
        )

        if (expiredRecordCount + expiredReportCount > 0) {
            logger.warn {
                "expired pending coaching feedback " +
                    "recordCount=$expiredRecordCount reportCount=$expiredReportCount"
            }
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
