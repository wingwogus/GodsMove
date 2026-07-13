package com.chamchamcham.application.coaching.reportfeedback.generation

import org.springframework.stereotype.Component

@Component
class ReportFeedbackRetrievalQueryPlanner {
    fun plan(context: ReportFeedbackContext): List<String> {
        val cropName = context.report.cropName.trim()
        val workTypeLabel = context.workType.label
        val statistics = context.report.statistics
        return buildList {
            add("$cropName $workTypeLabel 재배 관리")
            (statistics["recordCount"] as? Number)?.toInt()?.takeIf { it > 0 }
                ?.let { add("$cropName $workTypeLabel ${it}회 재배 관리") }
            (statistics["averageIntervalDays"] as? Number)
                ?.let { add("$cropName $workTypeLabel 평균간격 ${it}일 재배 관리") }
        }.distinct()
    }
}
