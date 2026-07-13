package com.chamchamcham.application.coaching.reportfeedback.generation

import org.springframework.stereotype.Component

@Component
class ReportFeedbackRetrievalQueryPlanner {
    fun plan(context: ReportFeedbackContext): List<String> {
        val cropName = context.report.cropName.trim()
        val workTypes = context.records.map(ReportFeedbackRecord::workType).distinct().sorted()
        val statistics = context.report.statistics
        val signals = buildList {
            if (statistics.watering.recordCount > 0) {
                add("관수 ${statistics.watering.recordCount}회" + statistics.watering.averageIntervalDays?.let { " 평균간격 ${it}일" }.orEmpty())
            }
            statistics.fertilizing.totalAmountKg?.let { add("시비 총량 ${it}kg") }
            statistics.pestControl.targets.forEach { add("병해충 방제 대상 ${it.target} ${it.count}회") }
            statistics.harvest.totalAmountKg?.let { add("수확량 ${it}kg") }
        }
        return buildList {
            add("$cropName 재배 주기 작업 관리")
            workTypes.forEach { add("$cropName $it 재배 관리") }
            signals.forEach { add("$cropName $it 재배 관리") }
        }.distinct()
    }
}
