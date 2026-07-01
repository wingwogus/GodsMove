package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component

@Component
class CoachingRetrievalFilterBuilder {
    fun build(command: CoachingRagCommand): String {
        val farmingConditions = mutableListOf(
            "sourceType == '${RagSourceType.FARMING_RECORD.name}'",
            "memberId == '${command.memberId}'"
        )

        command.farmId?.let { farmingConditions += "farmId == '$it'" }
        command.cropId?.let { farmingConditions += "cropId == '$it'" }
        command.workTypeId?.let { farmingConditions += "workTypeId == '$it'" }
        command.recordId?.let { farmingConditions += "recordId == '$it'" }
        command.periodStart?.let { farmingConditions += "workedAtEpochDay >= ${it.toEpochDay()}" }
        command.periodEnd?.let { farmingConditions += "workedAtEpochDay <= ${it.toEpochDay()}" }

        return "sourceType == '${RagSourceType.TECH_DOCUMENT.name}' || (${farmingConditions.joinToString(" && ")})"
    }
}
