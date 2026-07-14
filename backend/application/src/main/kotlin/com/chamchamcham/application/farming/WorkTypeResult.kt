package com.chamchamcham.application.farming

object WorkTypeResult {
    data class WorkTypeSummary(
        val code: String,
        val label: String,
        val detailRequired: Boolean,
        val fields: List<FieldSummary>
    )

    data class FieldSummary(
        val name: String,
        val type: FieldValueType,
        val required: Boolean,
        val options: List<EnumOptionSummary>
    )

    data class EnumOptionSummary(
        val code: String,
        val label: String
    )
}

enum class FieldValueType { STRING, INT, DECIMAL, ENUM }
