package com.chamchamcham.api.policy.dto

import com.chamchamcham.application.policy.PolicyRecommendationResult
import com.chamchamcham.application.policy.PolicySyncResult
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.chamchamcham.domain.policy.PolicySyncTriggerType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

object PolicyResponses {
    data class RecommendationPageResponse(
        val items: List<RecommendationItemResponse>,
        val nextCursor: String?
    ) {
        companion object {
            fun from(result: PolicyRecommendationResult.Page): RecommendationPageResponse =
                RecommendationPageResponse(
                    items = result.items.map(RecommendationItemResponse::from),
                    nextCursor = result.nextCursor
                )
        }
    }

    data class RecommendationItemResponse(
        val recommendationId: UUID,
        val policyProgramId: UUID,
        val programTitle: String,
        val eligibilitySummary: String,
        val benefitSummary: String,
        val applicationPeriodLabel: String,
        val agencyName: String,
        val score: BigDecimal,
        val reason: String
    ) {
        companion object {
            fun from(result: PolicyRecommendationResult.Card): RecommendationItemResponse =
                RecommendationItemResponse(
                    recommendationId = result.recommendationId,
                    policyProgramId = result.policyProgramId,
                    programTitle = result.programTitle,
                    eligibilitySummary = result.eligibilitySummary,
                    benefitSummary = result.benefitSummary,
                    applicationPeriodLabel = result.applicationPeriodLabel,
                    agencyName = result.agencyName,
                    score = result.score,
                    reason = result.reason
                )
        }
    }

    data class PolicyDetailResponse(
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
        val contacts: List<ContactResponse>,
        val attachments: List<AttachmentResponse>
    ) {
        companion object {
            fun from(result: PolicyRecommendationResult.Detail): PolicyDetailResponse =
                PolicyDetailResponse(
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
                    contacts = result.contacts.map(ContactResponse::from),
                    attachments = result.attachments.map(AttachmentResponse::from)
                )
        }
    }

    data class ContactResponse(
        val agencyName: String?,
        val departmentName: String?,
        val phoneNumber: String?
    ) {
        companion object {
            fun from(result: PolicyRecommendationResult.Contact): ContactResponse =
                ContactResponse(
                    agencyName = result.agencyName,
                    departmentName = result.departmentName,
                    phoneNumber = result.phoneNumber
                )
        }
    }

    data class AttachmentResponse(
        val fileName: String?,
        val extension: String?,
        val sizeBytes: Long?,
        val url: String?
    ) {
        companion object {
            fun from(result: PolicyRecommendationResult.Attachment): AttachmentResponse =
                AttachmentResponse(
                    fileName = result.fileName,
                    extension = result.extension,
                    sizeBytes = result.sizeBytes,
                    url = result.url
                )
        }
    }

    data class PolicySyncJobSummaryResponse(
        val jobId: UUID,
        val status: PolicySyncJobStatus,
        val targetYear: String
    ) {
        companion object {
            fun from(result: PolicySyncResult.JobSummary): PolicySyncJobSummaryResponse =
                PolicySyncJobSummaryResponse(
                    jobId = result.jobId,
                    status = result.status,
                    targetYear = result.targetYear
                )
        }
    }

    data class PolicySyncJobDetailResponse(
        val jobId: UUID,
        val source: PolicySource,
        val targetYear: String,
        val triggerType: PolicySyncTriggerType,
        val status: PolicySyncJobStatus,
        val totalCount: Int,
        val syncedCount: Int,
        val detailSuccessCount: Int,
        val detailFailureCount: Int,
        val errorMessage: String?,
        val startedAt: LocalDateTime,
        val finishedAt: LocalDateTime?
    ) {
        companion object {
            fun from(result: PolicySyncResult.JobDetail): PolicySyncJobDetailResponse =
                PolicySyncJobDetailResponse(
                    jobId = result.jobId,
                    source = result.source,
                    targetYear = result.targetYear,
                    triggerType = result.triggerType,
                    status = result.status,
                    totalCount = result.totalCount,
                    syncedCount = result.syncedCount,
                    detailSuccessCount = result.detailSuccessCount,
                    detailFailureCount = result.detailFailureCount,
                    errorMessage = result.errorMessage,
                    startedAt = result.startedAt,
                    finishedAt = result.finishedAt
                )
        }
    }
}
