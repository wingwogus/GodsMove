package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class RecordFeedbackPreparationListener(
    private val preparationService: RecordFeedbackPreparationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: RecordFeedbackPreparationRequested) {
        runCatching { preparationService.prepare(event) }
            .onFailure { exception -> logger.error(exception) { "record feedback context preparation failed" } }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
