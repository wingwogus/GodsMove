package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemSection

object ReportFeedbackOutputValidator {
    fun validate(
        content: ReportFeedbackContent,
        context: ReportFeedbackContext,
        documents: List<ReportFeedbackEvidence>,
    ): List<String> {
        val warnings = mutableListOf<String>()
        if (content.summary.isBlank()) {
            warnings += "summary_blank"
        } else if (content.summary.length !in MIN_SUMMARY_TEXT_LENGTH..MAX_TEXT_LENGTH) {
            warnings += "summary_text_length"
        }
        if (context.comparisons.isEmpty()) {
            if (content.comparisons.isNotEmpty()) {
                warnings += "comparison_not_available"
            }
        } else if (content.comparisons.size != 1) {
            warnings += "comparison_count"
        }
        if (content.strengths.size != 1) {
            warnings += "strength_count"
        }
        if (content.improvements.size != 1) {
            warnings += "improvement_count"
        }
        if (content.nextActions.size != 1) {
            warnings += "next_action_count"
        }
        val items = content.items()

        val currentReportRef = "report:${context.report.id}"
        val previousReportRef = context.previousReport?.let { "report:${it.id}" }
        val allowedRefs = buildSet {
            add(currentReportRef)
            context.records.forEach { add("record:${it.id}") }
            context.previousReport?.let { add("report:${it.id}") }
            documents.forEach { add(it.id) }
        }
        items.forEach { structured ->
            val item = structured.item
            if (item.basis.isBlank()) {
                warnings += "${structured.section.name.lowercase()}_basis_blank"
            }
            if (item.text.isBlank()) {
                warnings += "${structured.section.name.lowercase()}_text_blank"
            } else if (item.text.length !in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH) {
                warnings += "${structured.section.name.lowercase()}_text_length"
            }
            if (item.evidenceRefs.none { it.isNotBlank() }) {
                warnings += "${structured.section.name.lowercase()}_evidence_refs_blank"
            }
            item.evidenceRefs.filter { it.isNotBlank() && it !in allowedRefs }
                .forEach { warnings += "unknown_evidence:$it" }
            if (
                structured.section == ReportFeedbackItemSection.COMPARISON &&
                context.comparisons.isNotEmpty()
            ) {
                if (currentReportRef !in item.evidenceRefs) {
                    warnings += "comparison_current_report_ref_required"
                }
                if (previousReportRef == null || previousReportRef !in item.evidenceRefs) {
                    warnings += "comparison_previous_report_ref_required"
                }
            }
        }
        return warnings.distinct()
    }

    private const val MIN_SUMMARY_TEXT_LENGTH = 20
    private const val MIN_TEXT_LENGTH = 20
    private const val MAX_TEXT_LENGTH = 65
}
