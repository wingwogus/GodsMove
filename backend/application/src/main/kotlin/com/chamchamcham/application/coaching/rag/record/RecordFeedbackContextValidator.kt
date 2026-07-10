package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.stereotype.Component

data class RecordFeedbackContextValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

@Component
class RecordFeedbackContextValidator {
    fun validate(context: RecordFeedbackContext): RecordFeedbackContextValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (context.schemaVersion != RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION) {
            errors += "invalid_schema_version"
        }
        if (context.farm.name.isBlank()) {
            errors += "farm_name_blank"
        }
        if (context.farm.roadAddress.isBlank()) {
            errors += "farm_road_address_blank"
        }
        if (context.crop.name.isBlank()) {
            errors += "crop_name_blank"
        }
        if (context.record.memo.isBlank()) {
            errors += "record_memo_blank"
        }
        if (context.record.photoCount < 0) {
            errors += "photo_count_negative"
        }
        warnings += context.warnings

        return RecordFeedbackContextValidationResult(
            errors = errors.distinct(),
            warnings = warnings.distinct(),
        )
    }

    fun requireValid(context: RecordFeedbackContext): RecordFeedbackContextValidationResult {
        val result = validate(context)
        if (!result.isValid) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return result
    }
}
