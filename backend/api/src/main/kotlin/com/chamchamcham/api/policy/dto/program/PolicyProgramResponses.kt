package com.chamchamcham.api.policy.dto.program

import com.chamchamcham.application.policy.recommendation.PolicyRecommendationResult
import java.util.UUID

data class PolicyProgramDetailResponse(
    val id: UUID,
    val programTitle: String,
    val sourceYear: String,
    val agencyName: String,
    val departmentName: String?,
    val applicationPeriodLabel: String,
    val onlineApplyAvailable: Boolean,
    val sourceUrl: String?,
    val applicationUrl: String?,
    val purpose: String?,
    val summary: String?,
    val eligibility: String?,
    val benefit: String?,
    val applicationMethod: String?,
    val requiredDocuments: String?,
    val selectionCriteria: String?,
    val contacts: List<PolicyContactResponse>,
    val attachments: List<PolicyAttachmentResponse>
) {
    companion object {
        fun from(result: PolicyRecommendationResult.Detail): PolicyProgramDetailResponse =
            PolicyProgramDetailResponse(
                id = result.id,
                programTitle = result.programTitle,
                sourceYear = result.sourceYear,
                agencyName = result.agencyName,
                departmentName = result.departmentName,
                applicationPeriodLabel = result.applicationPeriodLabel,
                onlineApplyAvailable = result.onlineApplyAvailable,
                sourceUrl = result.sourceUrl,
                applicationUrl = result.applicationUrl,
                purpose = result.purpose,
                summary = result.summary,
                eligibility = result.eligibility,
                benefit = result.benefit,
                applicationMethod = result.applicationMethod,
                requiredDocuments = result.requiredDocuments,
                selectionCriteria = result.selectionCriteria,
                contacts = result.contacts.map(PolicyContactResponse::from),
                attachments = result.attachments.map(PolicyAttachmentResponse::from)
            )
    }
}

data class PolicyContactResponse(
    val agencyName: String?,
    val departmentName: String?,
    val phoneNumber: String?
) {
    companion object {
        fun from(result: PolicyRecommendationResult.Contact): PolicyContactResponse =
            PolicyContactResponse(
                agencyName = result.agencyName,
                departmentName = result.departmentName,
                phoneNumber = result.phoneNumber
            )
    }
}

data class PolicyAttachmentResponse(
    val fileName: String?,
    val extension: String?,
    val sizeBytes: Long?,
    val url: String?
) {
    companion object {
        fun from(result: PolicyRecommendationResult.Attachment): PolicyAttachmentResponse =
            PolicyAttachmentResponse(
                fileName = result.fileName,
                extension = result.extension,
                sizeBytes = result.sizeBytes,
                url = result.url
            )
    }
}
