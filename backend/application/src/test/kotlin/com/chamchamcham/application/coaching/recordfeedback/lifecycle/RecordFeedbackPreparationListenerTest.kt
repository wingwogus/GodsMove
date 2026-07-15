package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionalEventListener

class RecordFeedbackPreparationListenerTest {
    @Test
    fun `record feedback preparation listener is the only async entry point`() {
        val preparationOn = RecordFeedbackPreparationListener::class.java.getDeclaredMethod(
            "on",
            RecordFeedbackPreparationRequested::class.java,
        )
        val generationOn = RecordFeedbackGenerationListener::class.java.getDeclaredMethod(
            "on",
            RecordFeedbackGenerationRequested::class.java,
        )

        assertThat(preparationOn.getAnnotation(Async::class.java).value)
            .isEqualTo("coachingTaskExecutor")
        assertThat(generationOn.isAnnotationPresent(Async::class.java)).isFalse()
        assertThat(generationOn.isAnnotationPresent(EventListener::class.java)).isTrue()
        assertThat(generationOn.isAnnotationPresent(TransactionalEventListener::class.java)).isFalse()
    }
}
