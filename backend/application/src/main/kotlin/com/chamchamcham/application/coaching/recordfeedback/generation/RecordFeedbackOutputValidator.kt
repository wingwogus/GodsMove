package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory

object RecordFeedbackOutputValidator {
    fun validate(
        content: RecordFeedbackContent,
        context: RecordFeedbackContext,
        evidence: List<RecordFeedbackEvidence>,
    ): List<String> {
        val allowedEvidenceRefs = allowedEvidenceRefs(context, evidence)
        val warnings = mutableListOf<String>()

        validateItem("good_point", content.goodPoint, allowedEvidenceRefs, warnings)

        if (content.nextActions.size !in MIN_ACTION_COUNT..MAX_ACTION_COUNT) {
            warnings += "action_count"
        }
        content.nextActions.forEachIndexed { index, action ->
            validateAction(index, action, allowedEvidenceRefs, warnings)
        }

        return warnings.distinct()
    }

    private fun allowedEvidenceRefs(
        context: RecordFeedbackContext,
        evidence: List<RecordFeedbackEvidence>,
    ): AllowedEvidenceRefs {
        val weatherIds = buildSet {
            if (context.weather != null) {
                add(WEATHER_CURRENT_ID)
                context.weather.forecastDays.forEach { add("weather:${it.date}") }
            }
        }
        val documentIds = evidence
            .map { it.id }
            .filter { it.isNotBlank() }
            .toCollection(linkedSetOf())

        return AllowedEvidenceRefs(
            ids = linkedSetOf(context.recordCitationId()) + weatherIds + documentIds,
            weatherIds = weatherIds,
            documentIds = documentIds,
        )
    }

    private fun validateAction(
        index: Int,
        action: RecordFeedbackAction,
        allowedEvidenceRefs: AllowedEvidenceRefs,
        warnings: MutableList<String>,
    ) {
        validateItem("next_action_$index", action.asGoodPoint(), allowedEvidenceRefs, warnings)

        if (
            action.category == RecordFeedbackActionCategory.WEATHER &&
            action.evidenceRefs.none { it in allowedEvidenceRefs.weatherIds }
        ) {
            warnings += "weather_action_without_weather_evidence"
        }
        if (
            action.category == RecordFeedbackActionCategory.PEST_DISEASE &&
            action.evidenceRefs.none { it in allowedEvidenceRefs.documentIds }
        ) {
            warnings += "pest_disease_action_without_document_evidence"
        }
    }

    private fun validateItem(
        prefix: String,
        item: RecordFeedbackGoodPoint,
        allowedEvidenceRefs: AllowedEvidenceRefs,
        warnings: MutableList<String>,
    ) {
        if (item.basis.isBlank()) {
            warnings += "${prefix}_basis_blank"
        }
        if (item.text.isBlank()) {
            warnings += "${prefix}_text_blank"
        }
        val blankEvidenceRefs = item.evidenceRefs.filter { it.isBlank() }
        if (item.evidenceRefs.none { it.isNotBlank() }) {
            warnings += "${prefix}_evidence_refs_blank"
        }
        if (blankEvidenceRefs.isNotEmpty()) {
            warnings += "${prefix}_evidence_ref_blank"
        }
        if (item.text.length !in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH) {
            warnings += "${prefix}_text_length"
        }
        item.evidenceRefs
            .filter { it.isNotBlank() }
            .filterNot { it in allowedEvidenceRefs.ids }
            .forEach { warnings += "unknown_evidence:$it" }
    }

    private fun RecordFeedbackAction.asGoodPoint(): RecordFeedbackGoodPoint {
        return RecordFeedbackGoodPoint(
            basis = basis,
            text = text,
            evidenceRefs = evidenceRefs,
        )
    }

    private data class AllowedEvidenceRefs(
        val ids: Set<String>,
        val weatherIds: Set<String>,
        val documentIds: Set<String>,
    )

    private const val WEATHER_CURRENT_ID = "weather:current"
    private const val MIN_ACTION_COUNT = 2
    private const val MAX_ACTION_COUNT = 3
    private const val MIN_TEXT_LENGTH = 15
    private const val MAX_TEXT_LENGTH = 25
}
