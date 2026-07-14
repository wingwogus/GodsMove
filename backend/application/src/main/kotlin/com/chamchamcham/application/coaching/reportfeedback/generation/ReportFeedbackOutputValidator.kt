package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.CoachingTextPolicy
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
        }
        if (!content.summary.hasFriendlyHonorificTone()) {
            warnings += "summary_text_tone"
        }
        if (CoachingTextPolicy.containsEnglishLetter(content.summary)) {
            warnings += "summary_text_english"
        }
        val items = content.items()
        if (content.comparisons.isNotEmpty() && context.comparisons.isEmpty()) {
            warnings += "comparison_not_available"
        }

        val currentReportRef = "report:${context.report.id}"
        val previousReportRef = context.previousReport?.let { "report:${it.id}" }
        val allowedRefs = buildSet {
            add(currentReportRef)
            context.records.forEach { add("record:${it.id}") }
            context.previousReport?.let { add("report:${it.id}") }
            documents.forEach { add(it.id) }
        }
        val seen = mutableSetOf<String>()
        items.forEach { structured ->
            val item = structured.item
            if (item.basis.isBlank()) {
                warnings += "${structured.section.name.lowercase()}_basis_blank"
            }
            if (item.text.isBlank()) {
                warnings += "${structured.section.name.lowercase()}_text_blank"
            }
            if (!item.text.hasFriendlyHonorificTone()) {
                warnings += "${structured.section.name.lowercase()}_text_tone"
            }
            if (CoachingTextPolicy.containsEnglishLetter(item.text)) {
                warnings += "${structured.section.name.lowercase()}_text_english"
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
            val key = item.text.lowercase().filter(Char::isLetterOrDigit)
            if (!seen.add(key)) {
                warnings += "duplicate_item"
            }
        }
        return warnings.distinct()
    }

    private fun String.hasFriendlyHonorificTone(): Boolean {
        return trimEnd().trimEnd('.', '!', '?', '…', '。').endsWith("요")
    }
}
