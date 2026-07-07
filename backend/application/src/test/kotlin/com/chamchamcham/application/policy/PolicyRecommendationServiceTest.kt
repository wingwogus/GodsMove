package com.chamchamcham.application.policy

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import com.chamchamcham.domain.policy.PolicyRecommendation
import com.chamchamcham.domain.policy.PolicyRecommendationQueryRepository
import com.chamchamcham.domain.policy.PolicyRecommendationRepository
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJob
import com.chamchamcham.domain.policy.PolicySyncJobRepository
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.chamchamcham.domain.policy.PolicySyncTriggerType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PolicyRecommendationServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val latestJobId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val oldJobId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val policyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val recommendationId = UUID.fromString("00000000-0000-0000-0000-000000000401")

    @Mock private lateinit var policySyncJobRepository: PolicySyncJobRepository
    @Mock private lateinit var policyProgramRepository: PolicyProgramRepository
    @Mock private lateinit var policyRecommendationRepository: PolicyRecommendationRepository
    @Mock private lateinit var policyRecommendationQueryRepository: PolicyRecommendationQueryRepository
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var memberProfileReader: PolicyMemberProfileReader

    private lateinit var service: PolicyRecommendationService
    private lateinit var cursorCodec: OpaqueCursorCodec
    private lateinit var member: Member
    private lateinit var latestJob: PolicySyncJob

    @BeforeEach
    fun setUp() {
        cursorCodec = OpaqueCursorCodec()
        service = PolicyRecommendationService(
            policySyncJobRepository = policySyncJobRepository,
            policyProgramRepository = policyProgramRepository,
            policyRecommendationRepository = policyRecommendationRepository,
            policyRecommendationQueryRepository = policyRecommendationQueryRepository,
            memberRepository = memberRepository,
            memberProfileReader = memberProfileReader,
            scorer = PolicyRecommendationScorer(PolicyRegionMatcher()),
            textListJsonCodec = TextListJsonCodec(),
            cursorCodec = cursorCodec,
            clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("UTC"))
        )
        member = Member(
            id = memberId,
            email = "member@example.com",
            passwordHash = "hash",
            birthDate = LocalDate.of(1995, 1, 1),
            experienceLevel = 2,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL
        )
        latestJob = syncJob(latestJobId)
    }

    @Test
    fun `list recommendations returns empty page when no successful sync exists`() {
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(null)

        val page = service.listRecommendations(memberId, cursor = null, size = 20)

        assertThat(page.items).isEmpty()
        assertThat(page.nextCursor).isNull()
        verifyNoInteractions(policyProgramRepository, policyRecommendationRepository, policyRecommendationQueryRepository)
    }

    @Test
    fun `list recommendations regenerates stale or missing member recommendations from latest sync job`() {
        val program = recommendableProgram()
        val row = recommendation(program)
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
        `when`(policyRecommendationRepository.existsByMember_IdAndSourceSyncJob_Id(memberId, latestJobId)).thenReturn(false)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(memberProfileReader.read(memberId)).thenReturn(
            PolicyMemberProfile(
                birthDate = member.birthDate,
                experienceLevel = member.experienceLevel,
                managementType = member.managementType,
                cropNames = setOf("참당귀"),
                cropUsePartCategories = setOf("ROOT_BARK"),
                farmRegionTokens = setOf("충청북도")
            )
        )
        `when`(policyProgramRepository.findRecommendableCandidates(latestJobId, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(listOf(program))
        `when`(policyRecommendationQueryRepository.findPage(any()))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(row)))

        val page = service.listRecommendations(memberId, cursor = null, size = 20)

        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().policyProgramId).isEqualTo(policyProgramId)
        assertThat(page.items.first().programTitle).isEqualTo("청년 약용작물 지원")
        assertThat(page.items.first().reason).isEqualTo("청년농 대상이고 재배 품목이 맞아요.")
        assertThat(page.nextCursor).isNull()
        verify(policyRecommendationRepository).deleteByMember_Id(memberId)
        val savedRecommendations = capturedSavedRecommendations()
        assertThat(savedRecommendations).hasSize(1)
        assertThat(savedRecommendations.first().sourceSyncJob.id).isEqualTo(latestJobId)
        assertThat(savedRecommendations.first().score.toDouble()).isGreaterThanOrEqualTo(70.0)
    }

    @Test
    fun `list recommendations rejects cursor from stale sync job as invalid input`() {
        val staleCursor = cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                sourceSyncJobId = oldJobId,
                score = BigDecimal("80.0"),
                applyEndsOn = LocalDate.of(2026, 6, 30),
                id = recommendationId
            )
        )
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
        `when`(policyRecommendationRepository.existsByMember_IdAndSourceSyncJob_Id(memberId, latestJobId)).thenReturn(true)

        val exception = assertThrows(BusinessException::class.java) {
            service.listRecommendations(memberId, cursor = staleCursor, size = 20)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verify(policyRecommendationQueryRepository, never()).findPage(any())
    }

    @Test
    fun `get program detail returns synced recommendable detail with contacts and attachments from raw payload`() {
        val program = recommendableProgram(
            rawPayload = """
                {
                  "bizPicList": [
                    {"instNm": "농림축산식품부", "deptNm": "친환경농업과", "telno": "044-201-2434"}
                  ],
                  "bizAtchFileList": [
                    {"atchFileNm": "시행지침.pdf", "fileExtnNm": "pdf", "fileSz": 1537272, "url": "https://example.test/file.pdf"}
                  ]
                }
            """.trimIndent()
        )
        `when`(memberRepository.existsById(memberId)).thenReturn(true)
        `when`(policyProgramRepository.findByIdAndDetailSyncedTrueAndRecommendableTrue(policyProgramId)).thenReturn(program)

        val detail = service.getProgramDetail(memberId, policyProgramId)

        assertThat(detail.id).isEqualTo(policyProgramId)
        assertThat(detail.programTitle).isEqualTo("청년 약용작물 지원")
        assertThat(detail.contacts.first().departmentName).isEqualTo("친환경농업과")
        assertThat(detail.contacts.first().phoneNumber).isEqualTo("044-201-2434")
        assertThat(detail.attachments.first().fileName).isEqualTo("시행지침.pdf")
        assertThat(detail.attachments.first().sizeBytes).isEqualTo(1537272L)
    }

    @Test
    fun `get program detail hides list only or non recommendable policies`() {
        `when`(memberRepository.existsById(memberId)).thenReturn(true)
        `when`(policyProgramRepository.findByIdAndDetailSyncedTrueAndRecommendableTrue(policyProgramId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.getProgramDetail(memberId, policyProgramId)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.POLICY_PROGRAM_NOT_FOUND)
    }

    private fun capturedSavedRecommendations(): List<PolicyRecommendation> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<PolicyRecommendation>>
        verify(policyRecommendationRepository).saveAll(captor.capture())
        return captor.value.toList()
    }

    private fun syncJob(id: UUID): PolicySyncJob =
        PolicySyncJob(
            id = id,
            source = PolicySource.NONGUP_EZ,
            targetYear = "2026",
            triggerType = PolicySyncTriggerType.SCHEDULED,
            status = PolicySyncJobStatus.SUCCEEDED,
            startedAt = LocalDateTime.of(2026, 4, 1, 0, 0),
            finishedAt = LocalDateTime.of(2026, 4, 1, 0, 1)
        )

    private fun recommendableProgram(rawPayload: String = "{}"): PolicyProgram =
        PolicyProgram(
            id = policyProgramId,
            title = "청년 약용작물 지원",
            body = "약용작물 재배 청년농 지원",
            region = "충청북도",
            targetManagementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
            applyStartsOn = LocalDate.of(2026, 3, 1),
            applyEndsOn = LocalDate.of(2026, 6, 30),
            sourceUrl = "https://example.test/policy",
            source = PolicySource.NONGUP_EZ,
            externalId = "AB000009",
            sourceYear = "2026",
            summary = "약용작물 지원",
            agencyName = "농림축산식품부",
            departmentName = "친환경농업과",
            onlineApplyAvailable = true,
            applicationUrl = "https://example.test/apply",
            applicationPeriodLabel = "2026.03.01~06.30",
            eligibilityOriginal = "만 40세 미만 청년농",
            eligibilitySummary = "청년농 대상",
            benefitOriginal = "약용작물 생산 지원",
            benefitSummary = "생산비 지원",
            purpose = "약용작물 재배 확대",
            applicationMethod = "온라인 신청",
            requiredDocuments = "신청서",
            selectionCriteria = "자격 확인",
            detailSynced = true,
            recommendable = true,
            targetTagsJson = """["YOUNG_FARMER","REGISTERED_FARMER"]""",
            cropTagsJson = """["MEDICINAL_CROP"]""",
            regionTagsJson = """["충청북도"]""",
            lastSyncedJob = latestJob,
            rawPayload = rawPayload
        )

    private fun recommendation(program: PolicyProgram): PolicyRecommendation =
        PolicyRecommendation(
            id = recommendationId,
            member = member,
            policyProgram = program,
            sourceSyncJob = latestJob,
            score = BigDecimal("96.0"),
            reason = "청년농 대상이고 재배 품목이 맞아요."
        )
}
