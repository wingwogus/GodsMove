package com.chamchamcham.application.policy.sync

import com.chamchamcham.application.policy.source.nongupez.NongupEzPolicyContact
import com.chamchamcham.application.policy.source.nongupez.NongupEzPolicyDetail
import com.chamchamcham.application.policy.source.nongupez.NongupEzPolicyListItem
import com.chamchamcham.application.policy.source.nongupez.NongupEzPolicySourceClient
import com.chamchamcham.application.policy.support.NongupEzPolicyTagExtractor
import com.chamchamcham.application.policy.support.PolicyBenefitCategory
import com.chamchamcham.application.policy.support.PolicyCardTextGenerator
import com.chamchamcham.application.policy.support.TextListJsonCodec
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJob
import com.chamchamcham.domain.policy.PolicySyncJobRepository
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PolicySyncServiceTest {
    private val adminMemberId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val jobId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @Mock private lateinit var sourceClient: NongupEzPolicySourceClient
    @Mock private lateinit var policyProgramRepository: PolicyProgramRepository
    @Mock private lateinit var policySyncJobRepository: PolicySyncJobRepository

    private lateinit var service: PolicySyncService
    private lateinit var persistedJob: PolicySyncJob

    @BeforeEach
    fun setUp() {
        service = PolicySyncService(
            sourceClient = sourceClient,
            policyProgramRepository = policyProgramRepository,
            policySyncJobRepository = policySyncJobRepository,
            cardTextGenerator = PolicyCardTextGenerator(),
            tagExtractor = NongupEzPolicyTagExtractor(),
            textListJsonCodec = TextListJsonCodec(),
            transactionTemplate = TransactionTemplate(TestTransactionManager())
        )
    }

    @Test
    fun `runSync upserts list and detail success into recommendable policy`() {
        val listItem = listItem()
        `when`(sourceClient.detectLatestYear()).thenAnswer {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            "2026"
        }
        `when`(sourceClient.fetchPrograms("2026")).thenAnswer {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            listOf(listItem)
        }
        `when`(sourceClient.fetchDetail("AB000009", "2026")).thenAnswer {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            detail()
        }
        `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
            .thenReturn(emptyList())
        stubJobSaveAndFind()

        val result = service.runScheduledSync()

        assertEquals(PolicySyncJobStatus.SUCCEEDED, result.status)
        val savedProgram = capturedProgram()
        assertTrue(savedProgram.detailSynced)
        assertTrue(savedProgram.recommendable)
        assertTrue(savedProgram.eligibilitySummary.length <= 19)
        assertEquals(
            "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=AB000009&bizYr=2026",
            savedProgram.sourceUrl
        )
    }

    @Test
    fun `sync loads existing policies once for source year instead of per policy`() {
        val existing = existingSyncedProgram()
        val newItem = listItem(
            externalId = "AB000010",
            title = "특용작물 시설 지원",
            summary = "특용작물 재배 시설을 지원합니다.",
            rawJson = """{"afbzCd":"AB000010"}"""
        )
        `when`(sourceClient.detectLatestYear()).thenReturn("2026")
        `when`(sourceClient.fetchPrograms("2026")).thenReturn(listOf(listItem(), newItem))
        `when`(sourceClient.fetchDetail("AB000009", "2026")).thenReturn(detail())
        `when`(sourceClient.fetchDetail("AB000010", "2026")).thenReturn(
            detail(
                externalId = "AB000010",
                title = "특용작물 시설 지원",
                summary = "특용작물 재배 시설 지원",
                eligibility = "농업경영정보를 등록한 특용작물 재배 농업인",
                benefit = "시설 설치비 지원",
                rawJson = """{"afbzCd":"AB000010","bizYr":"2026"}"""
            )
        )
        `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
            .thenReturn(listOf(existing))
        stubJobSaveAndFind()

        service.runScheduledSync()

        verify(policyProgramRepository, times(1))
            .findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026")
    }

    @Test
    fun `detail failure keeps job succeeded and policy non recommendable`() {
        `when`(sourceClient.detectLatestYear()).thenReturn("2026")
        `when`(sourceClient.fetchPrograms("2026")).thenReturn(listOf(listItem()))
        `when`(sourceClient.fetchDetail("AB000009", "2026")).thenThrow(RuntimeException("detail down"))
        `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
            .thenReturn(emptyList())
        stubJobSaveAndFind()

        val result = service.runScheduledSync()

        assertEquals(PolicySyncJobStatus.SUCCEEDED, result.status)
        assertEquals(1, persistedJob.detailFailureCount)
        assertEquals(0, persistedJob.detailSuccessCount)
        assertFalse(capturedProgram().detailSynced)
    }

    @Test
    fun `unchanged existing policy is not saved again`() {
        val existing = existingSyncedProgram()
        `when`(sourceClient.detectLatestYear()).thenReturn("2026")
        `when`(sourceClient.fetchPrograms("2026")).thenReturn(listOf(listItem()))
        `when`(sourceClient.fetchDetail("AB000009", "2026")).thenReturn(detail())
        `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
            .thenReturn(listOf(existing))
        stubJobSaveAndFind()

        service.runScheduledSync()

        verify(policyProgramRepository, never()).save(existing)
    }

    @Test
    fun `sync replaces legacy stored benefit summary with canonical label`() {
        val existing = existingSyncedProgram(benefitSummary = "직불/수당")
        `when`(sourceClient.detectLatestYear()).thenReturn("2026")
        `when`(sourceClient.fetchPrograms("2026")).thenReturn(listOf(listItem()))
        `when`(sourceClient.fetchDetail("AB000009", "2026")).thenReturn(detail())
        `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
            .thenReturn(listOf(existing))
        stubJobSaveAndFind()

        service.runScheduledSync()

        assertEquals(PolicyBenefitCategory.GRANT.label, existing.benefitSummary)
        verify(policyProgramRepository).save(existing)
    }

    @Test
    fun `policy missing from current source list becomes not recommendable`() {
        val missing = existingSyncedProgram()
        `when`(sourceClient.detectLatestYear()).thenReturn("2026")
        `when`(sourceClient.fetchPrograms("2026")).thenReturn(emptyList())
        `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
            .thenReturn(listOf(missing))
        stubJobSaveAndFind()

        service.runScheduledSync()

        assertFalse(missing.recommendable)
        verify(policyProgramRepository).save(missing)
    }

    @Test
    fun `whole source failure records failed job and keeps previous policies`() {
        `when`(sourceClient.detectLatestYear()).thenThrow(RuntimeException("condition down"))
        `when`(policySyncJobRepository.save(any(PolicySyncJob::class.java))).thenAnswer { invocation ->
            val job = invocation.arguments[0] as PolicySyncJob
            persistedJob = PolicySyncJob(
                id = jobId,
                source = job.source,
                targetYear = job.targetYear,
                triggerType = job.triggerType,
                status = job.status,
                startedAt = job.startedAt,
                finishedAt = job.finishedAt,
                totalCount = job.totalCount,
                syncedCount = job.syncedCount,
                detailSuccessCount = job.detailSuccessCount,
                detailFailureCount = job.detailFailureCount,
                errorMessage = job.errorMessage,
                createdByMemberId = job.createdByMemberId
            )
            persistedJob
        }

        val result = service.createAdminSyncJob(adminMemberId)

        assertEquals(PolicySyncJobStatus.FAILED, result.status)
        verify(policyProgramRepository, never()).deleteAll()
    }

    private fun stubJobSaveAndFind() {
        `when`(policySyncJobRepository.save(any(PolicySyncJob::class.java))).thenAnswer { invocation ->
            val job = invocation.arguments[0] as PolicySyncJob
            persistedJob = PolicySyncJob(
                id = jobId,
                source = job.source,
                targetYear = job.targetYear,
                triggerType = job.triggerType,
                status = job.status,
                startedAt = job.startedAt,
                createdByMemberId = job.createdByMemberId
            )
            persistedJob
        }
        `when`(policySyncJobRepository.findById(jobId)).thenAnswer {
            Optional.of(persistedJob)
        }
    }

    private fun capturedProgram(): PolicyProgram {
        val captor = ArgumentCaptor.forClass(PolicyProgram::class.java)
        verify(policyProgramRepository).save(captor.capture())
        return captor.value
    }

    private fun listItem(
        externalId: String = "AB000009",
        sourceYear: String = "2026",
        title: String = "친환경농업 직불 지원",
        summary: String? = "친환경 인증 농업인에게 직불금을 지원합니다.",
        rawJson: String = """{"afbzCd":"AB000009"}"""
    ): NongupEzPolicyListItem =
        NongupEzPolicyListItem(
            externalId = externalId,
            sourceYear = sourceYear,
            title = title,
            summary = summary,
            agencyName = "농림축산식품부",
            applyStartsOn = LocalDate.of(2026, 1, 1),
            applyEndsOn = LocalDate.of(2026, 12, 31),
            rawJson = rawJson
        )

    private fun detail(
        externalId: String = "AB000009",
        sourceYear: String = "2026",
        title: String = "친환경농업 직불 지원",
        summary: String = "친환경 인증 농업인 지원",
        eligibility: String = "농업경영정보를 등록하고 친환경인증을 받은 농업인",
        benefit: String = "인증단계별 직불금 지원",
        rawJson: String = """{"afbzCd":"AB000009","bizYr":"2026"}"""
    ): NongupEzPolicyDetail =
        NongupEzPolicyDetail(
            externalId = externalId,
            sourceYear = sourceYear,
            title = title,
            purpose = "친환경농업 확산",
            summary = summary,
            eligibility = eligibility,
            benefit = benefit,
            applyStartsOn = LocalDate.of(2026, 2, 1),
            applyEndsOn = LocalDate.of(2026, 3, 31),
            applicationMethod = "방문 신청",
            requiredDocuments = "신청서",
            selectionCriteria = "자격 확인",
            agencyName = "농림축산식품부",
            contacts = listOf(
                NongupEzPolicyContact(
                    agencyName = "농림축산식품부",
                    departmentName = "친환경농업과",
                    phoneNumber = "044-000-0000"
                )
            ),
            attachments = emptyList(),
            rawJson = rawJson
        )

    private fun existingSyncedProgram(
        benefitSummary: String = PolicyBenefitCategory.GRANT.label
    ): PolicyProgram =
        PolicyProgram(
            title = "친환경농업 직불 지원",
            body = "친환경농업 확산\n\n친환경 인증 농업인 지원\n\n농업경영정보를 등록하고 친환경인증을 받은 농업인\n\n인증단계별 직불금 지원",
            region = "전국",
            targetManagementType = null
        ).also {
            it.applyListFields(
                source = PolicySource.NONGUP_EZ,
                externalId = "AB000009",
                sourceYear = "2026",
                title = "친환경농업 직불 지원",
                summary = "친환경 인증 농업인에게 직불금을 지원합니다.",
                region = "전국",
                sourceUrl = "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=AB000009&bizYr=2026",
                agencyName = "농림축산식품부"
            )
            it.applyDetailFields(
                body = "친환경농업 확산\n\n친환경 인증 농업인 지원\n\n농업경영정보를 등록하고 친환경인증을 받은 농업인\n\n인증단계별 직불금 지원",
                purpose = "친환경농업 확산",
                eligibilityOriginal = "농업경영정보를 등록하고 친환경인증을 받은 농업인",
                eligibilitySummary = "인증 보유 농업인",
                benefitOriginal = "인증단계별 직불금 지원",
                benefitSummary = benefitSummary,
                applyStartsOn = LocalDate.of(2026, 2, 1),
                applyEndsOn = LocalDate.of(2026, 3, 31),
                applicationPeriodLabel = "2026.02.01~03.31",
                applicationPeriodNotice = null,
                applicationMethod = "방문 신청",
                requiredDocuments = "신청서",
                selectionCriteria = "자격 확인",
                departmentName = "친환경농업과",
                onlineApplyAvailable = false,
                applicationUrl = null,
                targetTagsJson = """["REGISTERED_FARMER"]""",
                cropTagsJson = """["ECO_FRIENDLY"]""",
                regionTagsJson = """["전국"]""",
                rawPayload = """{"afbzCd":"AB000009","bizYr":"2026"}""",
                recommendable = true
            )
        }

    private class TestTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
