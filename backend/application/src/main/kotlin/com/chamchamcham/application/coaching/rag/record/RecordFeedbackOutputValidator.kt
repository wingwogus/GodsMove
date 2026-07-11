package com.chamchamcham.application.coaching.rag.record

import org.springframework.stereotype.Component

data class RecordFeedbackAllowedEvidenceRefs(
    val ids: Set<String>,
    val weatherIds: Set<String>,
    val documentIds: Set<String>,
)

data class RecordFeedbackOutputValidation(
    val status: RecordFeedbackOutputValidationStatus,
    val warnings: List<String>,
) {
    val isValid: Boolean
        get() = status == RecordFeedbackOutputValidationStatus.PASS
}

enum class RecordFeedbackOutputValidationStatus {
    PASS,
    FAIL,
}

@Component
class RecordFeedbackOutputValidator {
    fun allowedEvidenceRefs(
        context: RecordFeedbackContext,
        evidence: List<RecordFeedbackEvidence>,
    ): RecordFeedbackAllowedEvidenceRefs {
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

        return RecordFeedbackAllowedEvidenceRefs(
            ids = linkedSetOf(context.recordCitationId()) + weatherIds + documentIds,
            weatherIds = weatherIds,
            documentIds = documentIds,
        )
    }

    fun validate(
        result: RecordFeedbackCoachingResult,
        allowedEvidenceRefs: RecordFeedbackAllowedEvidenceRefs,
    ): RecordFeedbackOutputValidation {
        val warnings = mutableListOf<String>()

        validateItem("good_point", result.goodPoint, allowedEvidenceRefs, warnings)

        if (result.nextActions.size !in MIN_ACTION_COUNT..MAX_ACTION_COUNT) {
            warnings += "action_count"
        }
        result.nextActions.forEachIndexed { index, action ->
            validateAction(index, action, allowedEvidenceRefs, warnings)
        }

        return RecordFeedbackOutputValidation(
            status = if (warnings.isEmpty()) {
                RecordFeedbackOutputValidationStatus.PASS
            } else {
                RecordFeedbackOutputValidationStatus.FAIL
            },
            warnings = warnings.distinct(),
        )
    }

    private fun validateAction(
        index: Int,
        action: RecordFeedbackNextAction,
        allowedEvidenceRefs: RecordFeedbackAllowedEvidenceRefs,
        warnings: MutableList<String>,
    ) {
        validateItem("next_action_$index", action.asItem(), allowedEvidenceRefs, warnings)

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
        item: RecordFeedbackItem,
        allowedEvidenceRefs: RecordFeedbackAllowedEvidenceRefs,
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
        if (item.basis.isNotBlank() && item.text.isNotBlank() && !hasBasisTokenInText(item.basis, item.text)) {
            warnings += "${prefix}_basis_token_missing"
        }
    }

    private fun RecordFeedbackNextAction.asItem(): RecordFeedbackItem {
        return RecordFeedbackItem(
            basis = basis,
            text = text,
            evidenceRefs = evidenceRefs,
        )
    }

    private fun hasBasisTokenInText(basis: String, text: String): Boolean {
        val normalizedText = normalize(text)
        return BASIS_TOKEN_REGEX.findAll(basis)
            .map { normalize(it.value) }
            .filter { it.length >= MIN_BASIS_TOKEN_LENGTH }
            .any { it in normalizedText }
    }

    private fun normalize(value: String): String {
        return value.lowercase().filter { it.isLetterOrDigit() }
    }

    private companion object {
        const val WEATHER_CURRENT_ID = "weather:current"
        const val MIN_ACTION_COUNT = 2
        const val MAX_ACTION_COUNT = 3
        const val MIN_TEXT_LENGTH = 15
        const val MAX_TEXT_LENGTH = 45
        const val MIN_BASIS_TOKEN_LENGTH = 2
        val BASIS_TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
    }
}
