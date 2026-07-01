package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import org.springframework.stereotype.Component

@Component
class CoachingFeedbackPersistencePolicy {
    fun shouldSave(command: CoachingRagCommand): Boolean {
        return when (command.mode) {
            CoachingMode.CHAT -> false
            CoachingMode.RECORD_AUTO,
            CoachingMode.REPORT_MANUAL -> true
        }
    }
}
