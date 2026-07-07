package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.ManagementType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "policy_program",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_policy_program_source_external_year",
            columnNames = ["source", "external_id", "source_year"]
        )
    ]
)
class PolicyProgram(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(nullable = false, columnDefinition = "text")
    var body: String,

    @Column(nullable = false, length = 128)
    var region: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_management_type", length = 32)
    var targetManagementType: ManagementType?,

    @Column(name = "apply_starts_on")
    var applyStartsOn: LocalDate? = null,

    @Column(name = "apply_ends_on")
    var applyEndsOn: LocalDate? = null,

    @Column(name = "source_url", length = 2048)
    var sourceUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var source: PolicySource = PolicySource.NONGUP_EZ,

    @Column(name = "external_id", nullable = false, length = 64)
    var externalId: String = "",

    @Column(name = "source_year", nullable = false, length = 4)
    var sourceYear: String = "",

    @Column(name = "summary", columnDefinition = "text")
    var summary: String? = null,

    @Column(name = "agency_name", nullable = false, length = 255)
    var agencyName: String = "",

    @Column(name = "department_name", length = 255)
    var departmentName: String? = null,

    @Column(name = "online_apply_available", nullable = false)
    var onlineApplyAvailable: Boolean = false,

    @Column(name = "application_url", length = 2048)
    var applicationUrl: String? = null,

    @Column(name = "application_period_label", nullable = false, length = 19)
    var applicationPeriodLabel: String = "접수기관문의",

    @Column(name = "application_period_notice", length = 255)
    var applicationPeriodNotice: String? = null,

    @Column(name = "eligibility_original", columnDefinition = "text")
    var eligibilityOriginal: String? = null,

    @Column(name = "eligibility_summary", nullable = false, length = 19)
    var eligibilitySummary: String = "상세 자격 확인",

    @Column(name = "benefit_original", columnDefinition = "text")
    var benefitOriginal: String? = null,

    @Column(name = "benefit_summary", nullable = false, length = 19)
    var benefitSummary: String = "상세 지원 확인",

    @Column(name = "purpose", columnDefinition = "text")
    var purpose: String? = null,

    @Column(name = "application_method", columnDefinition = "text")
    var applicationMethod: String? = null,

    @Column(name = "required_documents", columnDefinition = "text")
    var requiredDocuments: String? = null,

    @Column(name = "selection_criteria", columnDefinition = "text")
    var selectionCriteria: String? = null,

    @Column(name = "detail_synced", nullable = false)
    var detailSynced: Boolean = false,

    @Column(nullable = false)
    var recommendable: Boolean = false,

    @Column(name = "target_tags_json", nullable = false, columnDefinition = "text")
    var targetTagsJson: String = "[]",

    @Column(name = "crop_tags_json", nullable = false, columnDefinition = "text")
    var cropTagsJson: String = "[]",

    @Column(name = "region_tags_json", nullable = false, columnDefinition = "text")
    var regionTagsJson: String = "[]",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_synced_job_id")
    var lastSyncedJob: PolicySyncJob? = null,

    @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
    var rawPayload: String = "{}",
) : BaseTimeEntity() {
    fun applyListFields(
        source: PolicySource,
        externalId: String,
        sourceYear: String,
        title: String,
        summary: String?,
        region: String,
        sourceUrl: String?,
        agencyName: String,
        lastSyncedJob: PolicySyncJob
    ) {
        this.source = source
        this.externalId = externalId
        this.sourceYear = sourceYear
        this.title = title
        this.summary = summary
        this.body = summary ?: body
        this.region = region
        this.sourceUrl = sourceUrl
        this.agencyName = agencyName
        this.lastSyncedJob = lastSyncedJob
    }

    fun applyDetailFields(
        body: String,
        purpose: String?,
        eligibilityOriginal: String?,
        eligibilitySummary: String,
        benefitOriginal: String?,
        benefitSummary: String,
        applyStartsOn: LocalDate?,
        applyEndsOn: LocalDate?,
        applicationPeriodLabel: String,
        applicationPeriodNotice: String?,
        applicationMethod: String?,
        requiredDocuments: String?,
        selectionCriteria: String?,
        departmentName: String?,
        onlineApplyAvailable: Boolean,
        applicationUrl: String?,
        targetTagsJson: String,
        cropTagsJson: String,
        regionTagsJson: String,
        rawPayload: String,
        recommendable: Boolean,
        lastSyncedJob: PolicySyncJob
    ) {
        require(eligibilitySummary.length <= 19)
        require(benefitSummary.length <= 19)
        require(applicationPeriodLabel.length <= 19)
        this.body = body
        this.purpose = purpose
        this.eligibilityOriginal = eligibilityOriginal
        this.eligibilitySummary = eligibilitySummary
        this.benefitOriginal = benefitOriginal
        this.benefitSummary = benefitSummary
        this.applyStartsOn = applyStartsOn
        this.applyEndsOn = applyEndsOn
        this.applicationPeriodLabel = applicationPeriodLabel
        this.applicationPeriodNotice = applicationPeriodNotice
        this.applicationMethod = applicationMethod
        this.requiredDocuments = requiredDocuments
        this.selectionCriteria = selectionCriteria
        this.departmentName = departmentName
        this.onlineApplyAvailable = onlineApplyAvailable
        this.applicationUrl = applicationUrl
        this.targetTagsJson = targetTagsJson
        this.cropTagsJson = cropTagsJson
        this.regionTagsJson = regionTagsJson
        this.rawPayload = rawPayload
        this.detailSynced = true
        this.recommendable = recommendable
        this.lastSyncedJob = lastSyncedJob
    }

    fun markDetailSyncFailed(rawPayload: String) {
        this.detailSynced = false
        this.recommendable = false
        this.rawPayload = rawPayload
    }

    fun isOpenOn(today: LocalDate): Boolean {
        return applyEndsOn == null || !applyEndsOn!!.isBefore(today)
    }
}
