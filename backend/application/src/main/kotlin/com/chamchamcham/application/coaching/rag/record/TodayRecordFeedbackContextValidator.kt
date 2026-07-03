package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.CoachingMode
import org.springframework.stereotype.Component

data class RecordFeedbackContextValidationResult(
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

@Component
class TodayRecordFeedbackContextValidator {
    fun validate(context: TodayRecordFeedbackContext): RecordFeedbackContextValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (context.schemaVersion != TODAY_RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION) {
            errors += "invalid_schema_version"
        }
        if (context.feedbackRequestId.isBlank()) {
            errors += "feedback_request_id_blank"
        }
        if (context.mode != CoachingMode.RECORD_AUTO) {
            errors += "unsupported_mode:${context.mode.name}"
        }
        if (context.member.memberId.isBlank()) {
            errors += "member_id_blank"
        }
        if (context.farm.farmId.isBlank()) {
            errors += "farm_id_blank"
        }
        if (context.farm.address.isBlank()) {
            errors += "farm_address_blank"
        }
        if (context.crop.cropId.isBlank()) {
            errors += "crop_id_blank"
        }
        if (context.crop.name.isBlank()) {
            errors += "crop_name_blank"
        }
        if (context.targetRecord.recordId.isBlank()) {
            errors += "target_record_id_blank"
        }
        if (context.targetRecord.memo.isBlank()) {
            errors += "target_record_memo_blank"
        }
        if (context.targetRecord.photoCount < 0) {
            errors += "photo_count_negative"
        }
        if (context.cropCycle == null) {
            warnings += "crop_cycle_unknown"
        } else if (context.cropCycle.daysAfterPlanting < 0) {
            errors += "days_after_planting_negative"
        }

        return RecordFeedbackContextValidationResult(
            errors = errors.distinct(),
            warnings = warnings.distinct()
        )
    }

    fun requireValid(context: TodayRecordFeedbackContext): RecordFeedbackContextValidationResult {
        val result = validate(context)
        if (!result.isValid) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return result
    }
}
