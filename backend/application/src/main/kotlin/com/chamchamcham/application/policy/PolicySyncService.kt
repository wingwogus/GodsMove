package com.chamchamcham.application.policy

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.source.NongupEzPolicyListItem
import com.chamchamcham.application.policy.source.NongupEzPolicySourceClient
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJob
import com.chamchamcham.domain.policy.PolicySyncJobRepository
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.chamchamcham.domain.policy.PolicySyncTriggerType
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Year
import java.util.UUID

@Service
class PolicySyncService(
    private val sourceClient: NongupEzPolicySourceClient,
    private val policyProgramRepository: PolicyProgramRepository,
    private val policySyncJobRepository: PolicySyncJobRepository,
    private val cardTextGenerator: PolicyCardTextGenerator,
    private val tagExtractor: NongupEzPolicyTagExtractor,
    private val textListJsonCodec: TextListJsonCodec,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun createAdminSyncJob(adminMemberId: UUID): PolicySyncResult.JobSummary {
        val job = createJobWithDetectedYear(
            triggerType = PolicySyncTriggerType.ADMIN,
            createdByMemberId = adminMemberId
        )
        return PolicySyncResult.JobSummary.from(job)
    }

    fun runScheduledSync(): PolicySyncResult.JobSummary {
        val job = createJobWithDetectedYear(
            triggerType = PolicySyncTriggerType.SCHEDULED,
            createdByMemberId = null
        )
        if (job.status == PolicySyncJobStatus.RUNNING) {
            runExistingJob(requireNotNull(job.id))
            return transactionTemplate.execute {
                PolicySyncResult.JobSummary.from(findJob(requireNotNull(job.id)))
            } ?: error("Transaction did not return a policy sync job summary")
        }
        return PolicySyncResult.JobSummary.from(job)
    }

    fun runExistingJob(jobId: UUID) {
        val targetYear = transactionTemplate.execute { findJob(jobId).targetYear }
            ?: error("Transaction did not return a policy sync target year")
        try {
            val listItems = sourceClient.fetchPrograms(targetYear)
            var synced = 0
            var detailSuccess = 0
            var detailFailure = 0

            listItems.forEach { item ->
                val detailFields = try {
                    val detail = sourceClient.fetchDetail(item.externalId, item.sourceYear)
                    val tags = tagExtractor.extract(detail)
                    val periodLabel = cardTextGenerator.periodLabel(
                        detail.applyStartsOn,
                        detail.applyEndsOn,
                        null
                    )
                    DetailFields(
                        body = listOfNotNull(detail.purpose, detail.summary, detail.eligibility, detail.benefit)
                            .joinToString("\n\n")
                            .ifBlank { item.summary ?: item.title },
                        purpose = detail.purpose,
                        eligibilityOriginal = detail.eligibility,
                        eligibilitySummary = cardTextGenerator.eligibilitySummary(detail.eligibility),
                        benefitOriginal = detail.benefit,
                        benefitSummary = cardTextGenerator.benefitSummary(detail.benefit),
                        applyStartsOn = detail.applyStartsOn,
                        applyEndsOn = detail.applyEndsOn,
                        applicationPeriodLabel = periodLabel,
                        applicationPeriodNotice = if (periodLabel == UNKNOWN_PERIOD_LABEL) periodLabel else null,
                        applicationMethod = detail.applicationMethod,
                        requiredDocuments = detail.requiredDocuments,
                        selectionCriteria = detail.selectionCriteria,
                        departmentName = detail.contacts.firstOrNull()?.departmentName,
                        onlineApplyAvailable = false,
                        applicationUrl = null,
                        targetTagsJson = textListJsonCodec.encode(tags.targetTags),
                        cropTagsJson = textListJsonCodec.encode(tags.cropTags),
                        regionTagsJson = textListJsonCodec.encode(tags.regionTags),
                        rawPayload = detail.rawJson
                    )
                } catch (exception: Exception) {
                    null
                }

                upsertProgram(jobId, item, detailFields)
                if (detailFields == null) {
                    detailFailure += 1
                } else {
                    detailSuccess += 1
                }
                synced += 1
            }

            succeedJob(jobId, listItems.size, synced, detailSuccess, detailFailure)
        } catch (exception: Exception) {
            failJob(jobId, exception)
        }
    }

    fun getJob(jobId: UUID): PolicySyncResult.JobDetail {
        return transactionTemplate.execute {
            PolicySyncResult.JobDetail.from(findJob(jobId))
        } ?: error("Transaction did not return a policy sync job detail")
    }

    private fun detailUrl(externalId: String, year: String): String =
        "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=$externalId&bizYr=$year"

    private fun createJobWithDetectedYear(
        triggerType: PolicySyncTriggerType,
        createdByMemberId: UUID?
    ): PolicySyncJob {
        return try {
            val targetYear = sourceClient.detectLatestYear()
            transactionTemplate.execute {
                policySyncJobRepository.save(
                    PolicySyncJob(
                        source = PolicySource.NONGUP_EZ,
                        targetYear = targetYear,
                        triggerType = triggerType,
                        createdByMemberId = createdByMemberId
                    )
                )
            } ?: error("Transaction did not return a policy sync job")
        } catch (exception: Exception) {
            val failedJob = PolicySyncJob(
                source = PolicySource.NONGUP_EZ,
                targetYear = Year.now(clock).value.toString(),
                triggerType = triggerType,
                createdByMemberId = createdByMemberId
            )
            failedJob.fail(exception.message ?: exception.javaClass.simpleName)
            transactionTemplate.execute {
                policySyncJobRepository.save(failedJob)
            } ?: error("Transaction did not return a failed policy sync job")
        }
    }

    private fun upsertProgram(
        jobId: UUID,
        item: NongupEzPolicyListItem,
        detailFields: DetailFields?
    ) {
        transactionTemplate.executeWithoutResult {
            val job = findJob(jobId)
            val program = policyProgramRepository.findBySourceAndExternalIdAndSourceYear(
                PolicySource.NONGUP_EZ,
                item.externalId,
                item.sourceYear
            ) ?: PolicyProgram(
                title = item.title,
                body = item.summary ?: item.title,
                region = DEFAULT_REGION,
                targetManagementType = null
            )

            program.applyListFields(
                source = PolicySource.NONGUP_EZ,
                externalId = item.externalId,
                sourceYear = item.sourceYear,
                title = item.title,
                summary = item.summary,
                region = DEFAULT_REGION,
                sourceUrl = detailUrl(item.externalId, item.sourceYear),
                agencyName = item.agencyName,
                lastSyncedJob = job
            )

            if (detailFields == null) {
                program.markDetailSyncFailed(rawPayload = item.rawJson)
            } else {
                program.applyDetailFields(
                    body = detailFields.body,
                    purpose = detailFields.purpose,
                    eligibilityOriginal = detailFields.eligibilityOriginal,
                    eligibilitySummary = detailFields.eligibilitySummary,
                    benefitOriginal = detailFields.benefitOriginal,
                    benefitSummary = detailFields.benefitSummary,
                    applyStartsOn = detailFields.applyStartsOn,
                    applyEndsOn = detailFields.applyEndsOn,
                    applicationPeriodLabel = detailFields.applicationPeriodLabel,
                    applicationPeriodNotice = detailFields.applicationPeriodNotice,
                    applicationMethod = detailFields.applicationMethod,
                    requiredDocuments = detailFields.requiredDocuments,
                    selectionCriteria = detailFields.selectionCriteria,
                    departmentName = detailFields.departmentName,
                    onlineApplyAvailable = detailFields.onlineApplyAvailable,
                    applicationUrl = detailFields.applicationUrl,
                    targetTagsJson = detailFields.targetTagsJson,
                    cropTagsJson = detailFields.cropTagsJson,
                    regionTagsJson = detailFields.regionTagsJson,
                    rawPayload = detailFields.rawPayload,
                    recommendable = true,
                    lastSyncedJob = job
                )
            }

            policyProgramRepository.save(program)
        }
    }

    private fun succeedJob(
        jobId: UUID,
        totalCount: Int,
        syncedCount: Int,
        detailSuccessCount: Int,
        detailFailureCount: Int
    ) {
        transactionTemplate.executeWithoutResult {
            findJob(jobId).succeed(
                totalCount = totalCount,
                syncedCount = syncedCount,
                detailSuccessCount = detailSuccessCount,
                detailFailureCount = detailFailureCount
            )
        }
    }

    private fun failJob(jobId: UUID, exception: Exception) {
        transactionTemplate.executeWithoutResult {
            findJob(jobId).fail(exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun findJob(jobId: UUID): PolicySyncJob =
        policySyncJobRepository.findById(jobId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND, detail = jobId)
        }

    private data class DetailFields(
        val body: String,
        val purpose: String?,
        val eligibilityOriginal: String?,
        val eligibilitySummary: String,
        val benefitOriginal: String?,
        val benefitSummary: String,
        val applyStartsOn: java.time.LocalDate?,
        val applyEndsOn: java.time.LocalDate?,
        val applicationPeriodLabel: String,
        val applicationPeriodNotice: String?,
        val applicationMethod: String?,
        val requiredDocuments: String?,
        val selectionCriteria: String?,
        val departmentName: String?,
        val onlineApplyAvailable: Boolean,
        val applicationUrl: String?,
        val targetTagsJson: String,
        val cropTagsJson: String,
        val regionTagsJson: String,
        val rawPayload: String
    )

    private companion object {
        const val DEFAULT_REGION = "전국"
        const val UNKNOWN_PERIOD_LABEL = "접수기관문의"
    }
}
