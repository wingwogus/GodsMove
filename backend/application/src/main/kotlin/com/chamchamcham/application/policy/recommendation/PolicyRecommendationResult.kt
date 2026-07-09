package com.chamchamcham.application.policy.recommendation

import java.math.BigDecimal
import java.util.UUID

object PolicyRecommendationResult {
    data class Page(
        val items: List<Card>,
        val nextCursor: String?
    )

    data class Card(
        val recommendationId: UUID,
        val policyProgramId: UUID,
        val programTitle: String,
        val eligibilitySummary: String,
        val benefitSummary: String,
        val applicationPeriodLabel: String,
        val agencyName: String,
        val score: BigDecimal,
        val reason: String
    )

    data class Detail(
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
        val contacts: List<Contact>,
        val attachments: List<Attachment>
    )

    data class Contact(
        val agencyName: String?,
        val departmentName: String?,
        val phoneNumber: String?
    )

    data class Attachment(
        val fileName: String?,
        val extension: String?,
        val sizeBytes: Long?,
        val url: String?
    )
}
