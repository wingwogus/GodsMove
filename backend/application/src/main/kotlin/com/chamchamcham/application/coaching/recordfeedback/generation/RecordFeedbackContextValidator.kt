package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackGenerationFailure

object RecordFeedbackContextValidator {
    fun requireValid(context: RecordFeedbackContext): List<String> {
        val errors = mutableListOf<String>()

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

        if (errors.isNotEmpty()) {
            throw RecordFeedbackGenerationFailure(RecordFeedbackFailureCode.INVALID_CONTEXT)
        }
        return context.warnings.distinct()
    }
}
