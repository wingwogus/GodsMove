package com.chamchamcham.application.coaching.feedback

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class RecordFeedbackGenerationListener(
    private val processor: RecordFeedbackGenerationProcessor,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: RecordFeedbackGenerationRequested) {
        runCatching { processor.generate(event) }
            .onFailure { exception -> logger.error(exception) { "record feedback generation failed" } }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
