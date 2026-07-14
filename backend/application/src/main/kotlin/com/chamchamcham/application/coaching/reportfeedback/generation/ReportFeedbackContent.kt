package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemSection

data class ReportFeedbackContent(
    val summary: String,
    val comparisons: List<ReportFeedbackContentItem> = emptyList(),
    val strengths: List<ReportFeedbackContentItem> = emptyList(),
    val improvements: List<ReportFeedbackContentItem> = emptyList(),
    val nextActions: List<ReportFeedbackContentItem> = emptyList(),
) {
    fun items(): List<ReportFeedbackStructuredItem> = buildList {
        comparisons.forEach { add(ReportFeedbackStructuredItem(ReportFeedbackItemSection.COMPARISON, it)) }
        strengths.forEach { add(ReportFeedbackStructuredItem(ReportFeedbackItemSection.STRENGTH, it)) }
        improvements.forEach { add(ReportFeedbackStructuredItem(ReportFeedbackItemSection.IMPROVEMENT, it)) }
        nextActions.forEach { add(ReportFeedbackStructuredItem(ReportFeedbackItemSection.NEXT_ACTION, it)) }
    }
}

data class ReportFeedbackContentItem(
    val basis: String,
    val text: String,
    val evidenceRefs: List<String>,
)

data class ReportFeedbackStructuredItem(
    val section: ReportFeedbackItemSection,
    val item: ReportFeedbackContentItem,
)

data class ReportFeedbackEvidence(
    val id: String,
    val title: String,
    val content: String,
)

data class ReportFeedbackPrompt(
    val system: String,
    val user: String,
)
