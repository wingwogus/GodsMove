package com.chamchamcham.application.policy.source

import java.time.LocalDate

data class NongupEzPolicyListItem(
    val externalId: String,
    val sourceYear: String,
    val title: String,
    val summary: String?,
    val agencyName: String,
    val applyStartsOn: LocalDate?,
    val applyEndsOn: LocalDate?,
    val rawJson: String
)

data class NongupEzPolicyDetail(
    val externalId: String,
    val sourceYear: String,
    val title: String,
    val purpose: String?,
    val summary: String?,
    val eligibility: String?,
    val benefit: String?,
    val applyStartsOn: LocalDate?,
    val applyEndsOn: LocalDate?,
    val applicationMethod: String?,
    val requiredDocuments: String?,
    val selectionCriteria: String?,
    val agencyName: String,
    val contacts: List<NongupEzPolicyContact>,
    val attachments: List<NongupEzPolicyAttachment>,
    val rawJson: String
)

data class NongupEzPolicyContact(
    val agencyName: String?,
    val departmentName: String?,
    val phoneNumber: String?
)

data class NongupEzPolicyAttachment(
    val fileName: String?,
    val extension: String?,
    val sizeBytes: Long?,
    val url: String?
)
