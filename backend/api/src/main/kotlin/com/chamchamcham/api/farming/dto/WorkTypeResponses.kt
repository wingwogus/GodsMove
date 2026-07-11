package com.chamchamcham.api.farming.dto

import com.chamchamcham.application.farming.WorkTypeResult

object WorkTypeResponses {
    data class WorkTypeResponse(
        val code: String,
        val label: String,
        val detailRequired: Boolean,
        val fields: List<FieldResponse>
    ) {
        companion object {
            fun from(result: WorkTypeResult.WorkTypeSummary): WorkTypeResponse {
                return WorkTypeResponse(
                    code = result.code,
                    label = result.label,
                    detailRequired = result.detailRequired,
                    fields = result.fields.map(FieldResponse::from)
                )
            }
        }
    }

    data class FieldResponse(
        val name: String,
        val type: String,
        val required: Boolean,
        val options: List<EnumOptionResponse>
    ) {
        companion object {
            fun from(result: WorkTypeResult.FieldSummary): FieldResponse {
                return FieldResponse(
                    name = result.name,
                    type = result.type.name,
                    required = result.required,
                    options = result.options.map(EnumOptionResponse::from)
                )
            }
        }
    }

    data class EnumOptionResponse(
        val code: String,
        val label: String
    ) {
        companion object {
            fun from(result: WorkTypeResult.EnumOptionSummary): EnumOptionResponse {
                return EnumOptionResponse(code = result.code, label = result.label)
            }
        }
    }
}
