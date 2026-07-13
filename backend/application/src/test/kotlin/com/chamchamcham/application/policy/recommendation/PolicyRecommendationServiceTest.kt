package com.chamchamcham.application.policy.recommendation

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.support.PolicyBenefitCategory
import com.chamchamcham.application.policy.support.TextListJsonCodec
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import com.chamchamcham.domain.policy.PolicyRecommendation
import com.chamchamcham.domain.policy.PolicyRecommendationQueryRepository
import com.chamchamcham.domain.policy.PolicyRecommendationRepository
import com.chamchamcham.domain.policy.PolicyRecommendationSort
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
import org.mockito.Mockito.inOrder
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
    private val policyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val recommendationId = UUID.fromString("00000000-0000-0000-0000-000000000401")

    @Mock private lateinit var policySyncJobRepository: PolicySyncJobRepository
    @Mock private lateinit var policyProgramRepository: PolicyProgramRepository
    @Mock private lateinit var policyRecommendationRepository: PolicyRecommendationRepository
    @Mock private lateinit var policyRecommendationQueryRepository: PolicyRecommendationQueryRepository
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository
    @Mock private lateinit var farmRepository: FarmRepository

    private lateinit var service: PolicyRecommendationService
    private lateinit var cursorCodec: OpaqueCursorCodec
    private lateinit var member: Member
    private lateinit var latestJob: PolicySyncJob
    private lateinit var regionMatcher: PolicyRegionMatcher

    @BeforeEach
    fun setUp() {
        cursorCodec = OpaqueCursorCodec()
        regionMatcher = PolicyRegionMatcher()
        service = PolicyRecommendationService(
            policySyncJobRepository = policySyncJobRepository,
            policyProgramRepository = policyProgramRepository,
            policyRecommendationRepository = policyRecommendationRepository,
            policyRecommendationQueryRepository = policyRecommendationQueryRepository,
            memberRepository = memberRepository,
            memberCropRepository = memberCropRepository,
            farmRepository = farmRepository,
            regionMatcher = regionMatcher,
            scorer = PolicyRecommendationScorer(regionMatcher),
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
        `when`(policyProgramRepository.findRecommendableCandidates(PolicySource.NONGUP_EZ, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(listOf(program))
        `when`(
            policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(emptyList())
        `when`(
            policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(null)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        givenPolicyProfileData()
        `when`(policyRecommendationQueryRepository.findPage(recommendationSearchCondition()))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(row)))

        val page = service.listRecommendations(memberId, cursor = null, size = 20)

        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().policyProgramId).isEqualTo(policyProgramId)
        assertThat(page.items.first().programTitle).isEqualTo("청년 약용작물 지원")
        assertThat(page.items.first().reason).isEqualTo("청년농 대상이고 재배 품목이 맞아요.")
        assertThat(page.nextCursor).isNull()
        verify(policyRecommendationRepository).deleteByMemberIdAndPolicyProgramSourceAndSourceYear(
            memberId,
            PolicySource.NONGUP_EZ,
            "2026"
        )
        val savedRecommendations = capturedSavedRecommendations()
        assertThat(savedRecommendations).hasSize(1)
        assertThat(savedRecommendations.first().score.toDouble()).isGreaterThanOrEqualTo(70.0)
    }

    @Test
    fun `list recommendations regenerates when latest recommendations include a policy outside current candidates`() {
        val secondPolicyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val obsoletePolicyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000399")
        val firstProgram = recommendableProgram()
        val secondProgram = recommendableProgram(
            id = secondPolicyProgramId,
            title = "청년 특용작물 지원",
            cropTagsJson = """["SPECIAL_CROP"]"""
        )
        val row = recommendation(firstProgram)
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
        `when`(policyProgramRepository.findRecommendableCandidates(PolicySource.NONGUP_EZ, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(listOf(firstProgram, secondProgram))
        `when`(
            policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(listOf(obsoletePolicyProgramId))
        `when`(
            policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(LocalDateTime.of(2026, 3, 1, 0, 0))
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        givenPolicyProfileData()
        `when`(policyRecommendationQueryRepository.findPage(recommendationSearchCondition()))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(row)))

        service.listRecommendations(memberId, cursor = null, size = 20)

        verify(policyRecommendationRepository).deleteByMemberIdAndPolicyProgramSourceAndSourceYear(
            memberId,
            PolicySource.NONGUP_EZ,
            "2026"
        )
        val savedRecommendations = capturedSavedRecommendations()
        assertThat(savedRecommendations.map { it.policyProgram.id }).containsExactlyInAnyOrder(
            policyProgramId,
            secondPolicyProgramId
        )
    }

    @Test
    fun `list recommendations reuses latest recommendations when saved policies are a subset of candidates`() {
        val ineligiblePolicyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val eligibleProgram = recommendableProgram()
        val ineligibleProgram = recommendableProgram(
            id = ineligiblePolicyProgramId,
            title = "전남 청년 약용작물 지원",
            regionTagsJson = """["전라남도"]"""
        )
        val row = recommendation(eligibleProgram)
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
        `when`(policyProgramRepository.findRecommendableCandidates(PolicySource.NONGUP_EZ, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(listOf(eligibleProgram, ineligibleProgram))
        `when`(
            policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(listOf(policyProgramId))
        `when`(
            policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(LocalDateTime.of(2026, 3, 1, 0, 0))
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        givenPolicyProfileData()
        `when`(policyRecommendationQueryRepository.findPage(recommendationSearchCondition()))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(row)))

        val page = service.listRecommendations(memberId, cursor = null, size = 20)

        assertThat(page.items).hasSize(1)
        verify(policyRecommendationRepository, never()).deleteByMemberIdAndPolicyProgramSourceAndSourceYear(
            memberId,
            PolicySource.NONGUP_EZ,
            "2026"
        )
        verify(policyRecommendationRepository, never()).saveAll(any<Iterable<PolicyRecommendation>>())
    }

    @Test
    fun `list recommendations loads farms before crops through policy fetch join queries`() {
        val program = recommendableProgram()
        val row = recommendation(program)
        givenReusableRecommendations(program)
        `when`(policyRecommendationQueryRepository.findPage(recommendationSearchCondition()))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(row)))

        service.listRecommendations(memberId, cursor = null, size = 20)

        verify(farmRepository).findAllWithBoundaryCoordinatesByOwnerId(memberId)
        verify(farmRepository, never()).findByOwnerId(memberId)
        verify(memberCropRepository, never()).findByMemberId(memberId)
        inOrder(farmRepository, memberCropRepository).apply {
            verify(farmRepository).findAllWithBoundaryCoordinatesByOwnerId(memberId)
            verify(memberCropRepository).findAllWithCropByMemberId(memberId)
        }
    }

    @Test
    fun `list recommendations saves only eligible generated recommendations`() {
        val ineligiblePolicyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val eligibleProgram = recommendableProgram()
        val regionMismatchedProgram = recommendableProgram(
            id = ineligiblePolicyProgramId,
            title = "전남 청년 약용작물 지원",
            regionTagsJson = """["전라남도"]"""
        )
        val row = recommendation(eligibleProgram)
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
        `when`(policyProgramRepository.findRecommendableCandidates(PolicySource.NONGUP_EZ, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(listOf(eligibleProgram, regionMismatchedProgram))
        `when`(
            policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(emptyList())
        `when`(
            policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(null)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        givenPolicyProfileData()
        `when`(policyRecommendationQueryRepository.findPage(recommendationSearchCondition()))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(row)))

        service.listRecommendations(memberId, cursor = null, size = 20)

        val savedRecommendations = capturedSavedRecommendations()
        assertThat(savedRecommendations.map { it.policyProgram.id }).containsExactly(policyProgramId)
    }

    @Test
    fun `list recommendations passes benefit category and latest sort to query repository`() {
        val program = recommendableProgram()
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
        `when`(policyProgramRepository.findRecommendableCandidates(PolicySource.NONGUP_EZ, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(listOf(program))
        `when`(
            policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(listOf(policyProgramId))
        `when`(
            policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(LocalDateTime.of(2026, 3, 1, 0, 0))
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        givenPolicyProfileData()
        val expectedCondition = recommendationSearchCondition(
            benefitSummary = "융자·금융",
            sort = PolicyRecommendationSort.LATEST
        )
        `when`(policyRecommendationQueryRepository.findPage(expectedCondition))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(emptyList()))

        service.listRecommendations(
            memberId = memberId,
            cursor = null,
            size = 20,
            benefitCategory = PolicyBenefitCategory.FINANCE,
            sort = PolicyRecommendationSort.LATEST
        )

        val captor = ArgumentCaptor.forClass(PolicyRecommendationQueryRepository.SearchCondition::class.java)
        verify(policyRecommendationQueryRepository).findPage(captor.capture() ?: expectedCondition)
        assertThat(captor.value.benefitSummary).isEqualTo("융자·금융")
        assertThat(captor.value.sort).isEqualTo(PolicyRecommendationSort.LATEST)
    }

    @Test
    fun `recommended next cursor contains only recommended ordering fields and round trips to query cursor`() {
        val secondProgramId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val secondRecommendationId = UUID.fromString("00000000-0000-0000-0000-000000000402")
        val firstProgram = recommendableProgram()
        val secondProgram = recommendableProgram(id = secondProgramId, title = "후속 정책")
        val firstRow = recommendation(firstProgram)
        val secondRow = recommendation(secondProgram, id = secondRecommendationId, score = BigDecimal("90.0"))
        givenReusableRecommendations(firstProgram, secondProgram)
        val firstCondition = recommendationSearchCondition(size = 2)
        val cursorCondition = recommendationSearchCondition(
            cursor = PolicyRecommendationQueryRepository.Cursor(
                score = BigDecimal("96.0"),
                applyStartsOn = null,
                applyEndsOn = LocalDate.of(2026, 6, 30),
                id = recommendationId
            ),
            size = 2
        )
        `when`(policyRecommendationQueryRepository.findPage(firstCondition))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(firstRow, secondRow)))
        `when`(policyRecommendationQueryRepository.findPage(cursorCondition))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(emptyList()))

        val firstPage = service.listRecommendations(memberId, cursor = null, size = 1)
        val payload = cursorCodec.decode(
            requireNotNull(firstPage.nextCursor),
            PolicyRecommendationCursorPayload::class.java
        )

        assertThat(payload).isEqualTo(
            PolicyRecommendationCursorPayload(
                source = PolicySource.NONGUP_EZ,
                sourceYear = "2026",
                benefitCategory = null,
                sort = PolicyRecommendationSort.RECOMMENDED,
                score = BigDecimal("96.0"),
                applyStartsOn = null,
                applyEndsOn = LocalDate.of(2026, 6, 30),
                id = recommendationId
            )
        )

        service.listRecommendations(memberId, cursor = firstPage.nextCursor, size = 1)

        verify(policyRecommendationQueryRepository).findPage(cursorCondition)
    }

    @Test
    fun `latest next cursor contains only latest ordering fields and round trips to query cursor`() {
        val secondProgramId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val secondRecommendationId = UUID.fromString("00000000-0000-0000-0000-000000000402")
        val firstProgram = recommendableProgram()
        val secondProgram = recommendableProgram(id = secondProgramId, title = "후속 정책")
        val firstRow = recommendation(firstProgram)
        val secondRow = recommendation(secondProgram, id = secondRecommendationId, score = BigDecimal("90.0"))
        givenReusableRecommendations(firstProgram, secondProgram)
        val firstCondition = recommendationSearchCondition(sort = PolicyRecommendationSort.LATEST, size = 2)
        val cursorCondition = recommendationSearchCondition(
            sort = PolicyRecommendationSort.LATEST,
            cursor = PolicyRecommendationQueryRepository.Cursor(
                score = null,
                applyStartsOn = LocalDate.of(2026, 3, 1),
                applyEndsOn = null,
                id = recommendationId
            ),
            size = 2
        )
        `when`(policyRecommendationQueryRepository.findPage(firstCondition))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(firstRow, secondRow)))
        `when`(policyRecommendationQueryRepository.findPage(cursorCondition))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(emptyList()))

        val firstPage = service.listRecommendations(
            memberId = memberId,
            cursor = null,
            size = 1,
            sort = PolicyRecommendationSort.LATEST
        )
        val payload = cursorCodec.decode(
            requireNotNull(firstPage.nextCursor),
            PolicyRecommendationCursorPayload::class.java
        )

        assertThat(payload).isEqualTo(
            PolicyRecommendationCursorPayload(
                source = PolicySource.NONGUP_EZ,
                sourceYear = "2026",
                benefitCategory = null,
                sort = PolicyRecommendationSort.LATEST,
                score = null,
                applyStartsOn = LocalDate.of(2026, 3, 1),
                applyEndsOn = null,
                id = recommendationId
            )
        )

        service.listRecommendations(
            memberId = memberId,
            cursor = firstPage.nextCursor,
            size = 1,
            sort = PolicyRecommendationSort.LATEST
        )

        verify(policyRecommendationQueryRepository).findPage(cursorCondition)
    }

    @Test
    fun `list recommendations rejects recommended cursor without score before downstream reads`() {
        val cursor = cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                source = PolicySource.NONGUP_EZ,
                sourceYear = "2026",
                benefitCategory = null,
                sort = PolicyRecommendationSort.RECOMMENDED,
                score = null,
                applyStartsOn = null,
                applyEndsOn = LocalDate.of(2026, 6, 30),
                id = recommendationId
            )
        )
        givenLatestSyncJob()

        val exception = assertThrows(BusinessException::class.java) {
            service.listRecommendations(memberId, cursor = cursor, size = 20)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoRecommendationReads()
    }

    @Test
    fun `latest cursor with null start date reaches query as null group boundary`() {
        val program = recommendableProgram(applyStartsOn = null)
        givenReusableRecommendations(program)
        val cursor = cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                source = PolicySource.NONGUP_EZ,
                sourceYear = "2026",
                benefitCategory = null,
                sort = PolicyRecommendationSort.LATEST,
                score = null,
                applyStartsOn = null,
                applyEndsOn = null,
                id = recommendationId
            )
        )
        val expectedCondition = recommendationSearchCondition(
            sort = PolicyRecommendationSort.LATEST,
            cursor = PolicyRecommendationQueryRepository.Cursor(
                score = null,
                applyStartsOn = null,
                applyEndsOn = null,
                id = recommendationId
            )
        )
        `when`(policyRecommendationQueryRepository.findPage(expectedCondition))
            .thenReturn(PolicyRecommendationQueryRepository.SearchResult(emptyList()))

        service.listRecommendations(
            memberId = memberId,
            cursor = cursor,
            size = 20,
            sort = PolicyRecommendationSort.LATEST
        )

        verify(policyRecommendationQueryRepository).findPage(expectedCondition)
    }

    @Test
    fun `list recommendations rejects cursor with unknown source before downstream reads`() {
        val cursor = cursorCodec.encode(
            mapOf(
                "source" to "OTHER_SOURCE",
                "sourceYear" to "2026",
                "benefitCategory" to null,
                "sort" to "RECOMMENDED",
                "score" to "80.0",
                "applyStartsOn" to null,
                "applyEndsOn" to "2026-06-30",
                "id" to recommendationId.toString()
            )
        )
        givenLatestSyncJob()

        val exception = assertThrows(BusinessException::class.java) {
            service.listRecommendations(memberId, cursor = cursor, size = 20)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoRecommendationReads()
    }

    @Test
    fun `list recommendations rejects cursor bound to different benefit category before downstream reads`() {
        val cursor = cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                source = PolicySource.NONGUP_EZ,
                sourceYear = "2026",
                benefitCategory = PolicyBenefitCategory.GRANT.name,
                sort = PolicyRecommendationSort.RECOMMENDED,
                score = BigDecimal("80.0"),
                applyStartsOn = null,
                applyEndsOn = LocalDate.of(2026, 6, 30),
                id = recommendationId
            )
        )
        givenLatestSyncJob()

        val exception = assertThrows(BusinessException::class.java) {
            service.listRecommendations(
                memberId = memberId,
                cursor = cursor,
                size = 20,
                benefitCategory = PolicyBenefitCategory.FINANCE
            )
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoRecommendationReads()
    }

    @Test
    fun `list recommendations rejects cursor bound to a different sort before downstream reads`() {
        val mismatchedCursor = cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                source = PolicySource.NONGUP_EZ,
                sourceYear = "2026",
                benefitCategory = null,
                sort = PolicyRecommendationSort.RECOMMENDED,
                score = BigDecimal("80.0"),
                applyStartsOn = null,
                applyEndsOn = LocalDate.of(2026, 6, 30),
                id = recommendationId
            )
        )
        givenLatestSyncJob()

        val exception = assertThrows(BusinessException::class.java) {
            service.listRecommendations(
                memberId = memberId,
                cursor = mismatchedCursor,
                size = 20,
                sort = PolicyRecommendationSort.LATEST
            )
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoRecommendationReads()
    }

    @Test
    fun `list recommendations rejects cursor from stale sync job as invalid input`() {
        val staleCursor = cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                source = PolicySource.NONGUP_EZ,
                sourceYear = "2025",
                benefitCategory = null,
                sort = PolicyRecommendationSort.RECOMMENDED,
                score = BigDecimal("80.0"),
                applyStartsOn = null,
                applyEndsOn = LocalDate.of(2026, 6, 30),
                id = recommendationId
            )
        )
        givenLatestSyncJob()

        val exception = assertThrows(BusinessException::class.java) {
            service.listRecommendations(memberId, cursor = staleCursor, size = 20)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoRecommendationReads()
    }

    @Test
    fun `list recommendations rejects malformed cursor as invalid input`() {
        givenLatestSyncJob()

        val exception = assertThrows(BusinessException::class.java) {
            service.listRecommendations(memberId, cursor = "not-a-valid-cursor", size = 20)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoRecommendationReads()
    }

    @Test
    fun `list recommendations surfaces malformed stored tag json`() {
        val program = recommendableProgram(cropTagsJson = "{not-json")
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
        `when`(policyProgramRepository.findRecommendableCandidates(PolicySource.NONGUP_EZ, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(listOf(program))
        `when`(
            policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(emptyList())
        `when`(
            policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(null)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        givenPolicyProfileData()

        assertThrows(IllegalArgumentException::class.java) {
            service.listRecommendations(memberId, cursor = null, size = 20)
        }
        verifyNoInteractions(policyRecommendationQueryRepository)
    }

    @Test
    fun `get program detail returns synced recommendable detail with contacts and attachments from raw payload`() {
        val program = recommendableProgram(
            rawPayload = """
                {
                  "bizPicList": [
                    {"bizTkcgInstCdNm": "농림축산식품부", "bizTkcgDeptInstCdNm": "친환경농업과", "bizPicTelno": "044-201-2434"}
                  ],
                  "bizAtchFileList": [
                    {"originalName": "시행지침.pdf", "extension": "pdf", "size": 1537272, "url": "https://example.test/file.pdf"}
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

    @Test
    fun `search recommendations reads only from query repository and never touches regeneration writes`() {
        val program = recommendableProgram()
        val row = recommendation(program)
        val condition = PolicyRecommendationQueryRepository.MemberSearchCondition(
            memberId = memberId,
            keyword = "청년",
            cursorCreatedAt = null,
            cursorId = null,
            size = 21
        )
        `when`(policyRecommendationQueryRepository.searchByMember(condition)).thenReturn(listOf(row))

        val page = service.searchRecommendations(memberId, keyword = "청년", cursor = null, size = 20)

        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().policyProgramId).isEqualTo(policyProgramId)
        assertThat(page.nextCursor).isNull()
        verify(policyRecommendationQueryRepository).searchByMember(condition)
        verifyNoInteractions(
            policySyncJobRepository,
            policyProgramRepository,
            policyRecommendationRepository,
            memberRepository,
            memberCropRepository,
            farmRepository
        )
    }

    @Test
    fun `search recommendations returns next cursor when an extra row exists beyond page size`() {
        val secondProgramId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val secondRecommendationId = UUID.fromString("00000000-0000-0000-0000-000000000402")
        val firstProgram = recommendableProgram()
        val secondProgram = recommendableProgram(id = secondProgramId, title = "후속 정책")
        val firstRow = recommendation(firstProgram, createdAt = LocalDateTime.of(2026, 3, 20, 9, 0))
        val secondRow = recommendation(
            secondProgram,
            id = secondRecommendationId,
            createdAt = LocalDateTime.of(2026, 3, 10, 9, 0)
        )
        val condition = PolicyRecommendationQueryRepository.MemberSearchCondition(
            memberId = memberId,
            keyword = null,
            cursorCreatedAt = null,
            cursorId = null,
            size = 2
        )
        `when`(policyRecommendationQueryRepository.searchByMember(condition)).thenReturn(listOf(firstRow, secondRow))

        val page = service.searchRecommendations(memberId, keyword = null, cursor = null, size = 1)

        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().recommendationId).isEqualTo(recommendationId)
        val payload = cursorCodec.decode(
            requireNotNull(page.nextCursor),
            PolicyRecommendationSearchCursorPayload::class.java
        )
        assertThat(payload).isEqualTo(
            PolicyRecommendationSearchCursorPayload(createdAt = firstRow.createdAt, id = recommendationId)
        )
    }

    @Test
    fun `search recommendations returns no next cursor when rows fit within page size`() {
        val program = recommendableProgram()
        val row = recommendation(program)
        val condition = PolicyRecommendationQueryRepository.MemberSearchCondition(
            memberId = memberId,
            keyword = null,
            cursorCreatedAt = null,
            cursorId = null,
            size = 21
        )
        `when`(policyRecommendationQueryRepository.searchByMember(condition)).thenReturn(listOf(row))

        val page = service.searchRecommendations(memberId, keyword = null, cursor = null, size = 20)

        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `search recommendations decodes cursor into member search condition`() {
        val program = recommendableProgram()
        val row = recommendation(program)
        val payload = PolicyRecommendationSearchCursorPayload(
            createdAt = LocalDateTime.of(2026, 3, 1, 0, 0),
            id = recommendationId
        )
        val cursor = cursorCodec.encode(payload)
        val condition = PolicyRecommendationQueryRepository.MemberSearchCondition(
            memberId = memberId,
            keyword = null,
            cursorCreatedAt = payload.createdAt,
            cursorId = payload.id,
            size = 21
        )
        `when`(policyRecommendationQueryRepository.searchByMember(condition)).thenReturn(listOf(row))

        service.searchRecommendations(memberId, keyword = null, cursor = cursor, size = 20)

        verify(policyRecommendationQueryRepository).searchByMember(condition)
    }

    @Test
    fun `search recommendations rejects malformed cursor as invalid input without reading`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.searchRecommendations(memberId, keyword = null, cursor = "not-a-valid-cursor", size = 20)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoInteractions(
            policyRecommendationQueryRepository,
            policySyncJobRepository,
            policyProgramRepository,
            policyRecommendationRepository,
            memberRepository,
            memberCropRepository,
            farmRepository
        )
    }

    @Test
    fun `search recommendations rejects invalid size before reading`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.searchRecommendations(memberId, keyword = null, cursor = null, size = 0)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
        verifyNoInteractions(policyRecommendationQueryRepository)
    }

    private fun capturedSavedRecommendations(): List<PolicyRecommendation> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<PolicyRecommendation>>
        verify(policyRecommendationRepository).saveAll(captor.capture())
        return captor.value.toList()
    }

    private fun recommendationSearchCondition(
        benefitSummary: String? = null,
        sort: PolicyRecommendationSort = PolicyRecommendationSort.RECOMMENDED,
        cursor: PolicyRecommendationQueryRepository.Cursor? = null,
        size: Int = 21
    ): PolicyRecommendationQueryRepository.SearchCondition =
        PolicyRecommendationQueryRepository.SearchCondition(
            memberId = memberId,
            source = PolicySource.NONGUP_EZ,
            sourceYear = "2026",
            benefitSummary = benefitSummary,
            sort = sort,
            cursor = cursor,
            size = size
        )

    private fun givenLatestSyncJob() {
        `when`(
            policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
                PolicySource.NONGUP_EZ,
                PolicySyncJobStatus.SUCCEEDED
            )
        ).thenReturn(latestJob)
    }

    private fun givenReusableRecommendations(vararg programs: PolicyProgram) {
        givenLatestSyncJob()
        `when`(policyProgramRepository.findRecommendableCandidates(PolicySource.NONGUP_EZ, "2026", LocalDate.of(2026, 4, 1)))
            .thenReturn(programs.toList())
        `when`(
            policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(programs.map { requireNotNull(it.id) })
        `when`(
            policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
                memberId,
                PolicySource.NONGUP_EZ,
                "2026"
            )
        ).thenReturn(LocalDateTime.of(2026, 3, 1, 0, 0))
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        givenPolicyProfileData()
    }

    private fun verifyNoRecommendationReads() {
        verifyNoInteractions(
            policyProgramRepository,
            policyRecommendationRepository,
            memberRepository,
            memberCropRepository,
            farmRepository,
            policyRecommendationQueryRepository
        )
    }

    private fun givenPolicyProfileData() {
        val farm = farm()
        val crop = Crop(
            externalNo = 1001,
            name = "참당귀",
            usePartCategory = CropUsePartCategory.ROOT_BARK
        )
        `when`(memberCropRepository.findAllWithCropByMemberId(memberId)).thenReturn(
            listOf(
                MemberCrop(
                    member = member,
                    farm = farm,
                    crop = crop
                )
            )
        )
        `when`(farmRepository.findAllWithBoundaryCoordinatesByOwnerId(memberId)).thenReturn(listOf(farm))
    }

    private fun farm(): Farm =
        Farm(
            owner = member,
            name = "제천 약초밭",
            roadAddress = "충청북도 제천시 청풍면 약초로 1",
            jibunAddress = "충북 제천시 청풍면"
        )

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

    private fun recommendableProgram(
        id: UUID = policyProgramId,
        title: String = "청년 약용작물 지원",
        targetTagsJson: String = """["YOUNG_FARMER","REGISTERED_FARMER"]""",
        cropTagsJson: String = """["MEDICINAL_CROP"]""",
        regionTagsJson: String = """["충청북도"]""",
        applyStartsOn: LocalDate? = LocalDate.of(2026, 3, 1),
        applyEndsOn: LocalDate? = LocalDate.of(2026, 6, 30),
        rawPayload: String = "{}"
    ): PolicyProgram =
        PolicyProgram(
            id = id,
            title = title,
            body = "약용작물 재배 청년농 지원",
            region = "충청북도",
            targetManagementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
            applyStartsOn = applyStartsOn,
            applyEndsOn = applyEndsOn,
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
            targetTagsJson = targetTagsJson,
            cropTagsJson = cropTagsJson,
            regionTagsJson = regionTagsJson,
            rawPayload = rawPayload
        )

    private fun recommendation(
        program: PolicyProgram,
        id: UUID = recommendationId,
        score: BigDecimal = BigDecimal("96.0"),
        createdAt: LocalDateTime = LocalDateTime.of(2026, 3, 15, 9, 0)
    ): PolicyRecommendation =
        PolicyRecommendation(
            id = id,
            member = member,
            policyProgram = program,
            score = score,
            reason = "청년농 대상이고 재배 품목이 맞아요."
        ).also { setCreatedAt(it, createdAt) }

    private fun setCreatedAt(entity: PolicyRecommendation, createdAt: LocalDateTime) {
        BaseTimeEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(entity, createdAt)
        }
    }
}
