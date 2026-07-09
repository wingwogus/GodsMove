package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.ManagementType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
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
        agencyName: String
    ): Boolean {
        var changed = false

        fun <T> setIfChanged(current: T, next: T, setter: (T) -> Unit) {
            if (current != next) {
                setter(next)
                changed = true
            }
        }

        setIfChanged(this.source, source) { this.source = it }
        setIfChanged(this.externalId, externalId) { this.externalId = it }
        setIfChanged(this.sourceYear, sourceYear) { this.sourceYear = it }
        setIfChanged(this.title, title) { this.title = it }
        setIfChanged(this.summary, summary) { this.summary = it }
        if (!detailSynced) {
            setIfChanged(this.body, summary ?: body) { this.body = it }
        }
        setIfChanged(this.region, region) { this.region = it }
        setIfChanged(this.sourceUrl, sourceUrl) { this.sourceUrl = it }
        setIfChanged(this.agencyName, agencyName) { this.agencyName = it }
        return changed
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
        recommendable: Boolean
    ): Boolean {
        require(eligibilitySummary.length <= 19)
        require(benefitSummary.length <= 19)
        require(applicationPeriodLabel.length <= 19)

        var changed = false

        fun <T> setIfChanged(current: T, next: T, setter: (T) -> Unit) {
            if (current != next) {
                setter(next)
                changed = true
            }
        }

        setIfChanged(this.body, body) { this.body = it }
        setIfChanged(this.purpose, purpose) { this.purpose = it }
        setIfChanged(this.eligibilityOriginal, eligibilityOriginal) { this.eligibilityOriginal = it }
        setIfChanged(this.eligibilitySummary, eligibilitySummary) { this.eligibilitySummary = it }
        setIfChanged(this.benefitOriginal, benefitOriginal) { this.benefitOriginal = it }
        setIfChanged(this.benefitSummary, benefitSummary) { this.benefitSummary = it }
        setIfChanged(this.applyStartsOn, applyStartsOn) { this.applyStartsOn = it }
        setIfChanged(this.applyEndsOn, applyEndsOn) { this.applyEndsOn = it }
        setIfChanged(this.applicationPeriodLabel, applicationPeriodLabel) { this.applicationPeriodLabel = it }
        setIfChanged(this.applicationPeriodNotice, applicationPeriodNotice) { this.applicationPeriodNotice = it }
        setIfChanged(this.applicationMethod, applicationMethod) { this.applicationMethod = it }
        setIfChanged(this.requiredDocuments, requiredDocuments) { this.requiredDocuments = it }
        setIfChanged(this.selectionCriteria, selectionCriteria) { this.selectionCriteria = it }
        setIfChanged(this.departmentName, departmentName) { this.departmentName = it }
        setIfChanged(this.onlineApplyAvailable, onlineApplyAvailable) { this.onlineApplyAvailable = it }
        setIfChanged(this.applicationUrl, applicationUrl) { this.applicationUrl = it }
        setIfChanged(this.targetTagsJson, targetTagsJson) { this.targetTagsJson = it }
        setIfChanged(this.cropTagsJson, cropTagsJson) { this.cropTagsJson = it }
        setIfChanged(this.regionTagsJson, regionTagsJson) { this.regionTagsJson = it }
        setIfChanged(this.rawPayload, rawPayload) { this.rawPayload = it }
        setIfChanged(this.detailSynced, true) { this.detailSynced = it }
        setIfChanged(this.recommendable, recommendable) { this.recommendable = it }
        return changed
    }

    fun markDetailSyncFailed(rawPayload: String): Boolean {
        var changed = false
        if (detailSynced) {
            detailSynced = false
            changed = true
        }
        if (recommendable) {
            recommendable = false
            changed = true
        }
        if (this.rawPayload != rawPayload) {
            this.rawPayload = rawPayload
            changed = true
        }
        return changed
    }

    fun isOpenOn(today: LocalDate): Boolean {
        return applyEndsOn == null || !applyEndsOn!!.isBefore(today)
    }
}
