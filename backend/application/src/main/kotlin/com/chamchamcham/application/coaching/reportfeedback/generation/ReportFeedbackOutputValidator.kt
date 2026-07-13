package com.chamchamcham.application.coaching.reportfeedback.generation

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
        val items = content.items()

        val allowedRefs = buildSet {
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
            if (item.evidenceRefs.none { it.isNotBlank() }) {
                warnings += "${structured.section.name.lowercase()}_evidence_refs_blank"
            }
            item.evidenceRefs.filter { it.isNotBlank() && it !in allowedRefs }
                .forEach { warnings += "unknown_evidence:$it" }
            val key = listOf(structured.section.name, item.basis, item.text)
                .joinToString("|") { it.lowercase().filter(Char::isLetterOrDigit) }
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
