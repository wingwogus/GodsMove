package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RecordFeedbackGenerationListener(
    private val processor: RecordFeedbackGenerationProcessor,
) {
    @EventListener
    fun on(event: RecordFeedbackGenerationRequested) {
        runCatching { processor.generate(event) }
            .onFailure { exception -> logger.error(exception) { "record feedback generation failed" } }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
