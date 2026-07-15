package com.chamchamcham.application.coaching.chat

import com.chamchamcham.application.coaching.common.RagSourceType
import org.springframework.stereotype.Component

@Component
class CoachingRetrievalFilterBuilder {
    fun build(): String {
        return "sourceType == '${RagSourceType.TECH_DOCUMENT.name}'"
    }
}
