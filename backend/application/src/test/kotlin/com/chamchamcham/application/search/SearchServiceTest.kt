package com.chamchamcham.application.search

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramQueryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SearchServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @Mock private lateinit var farmingRecordService: FarmingRecordService
    @Mock private lateinit var communityPostService: CommunityPostService
    @Mock private lateinit var policyProgramQueryRepository: PolicyProgramQueryRepository

    private val cursorCodec = OpaqueCursorCodec()

    private lateinit var service: SearchService

    @BeforeEach
    fun setUp() {
        service = SearchService(
            farmingRecordService = farmingRecordService,
            communityPostService = communityPostService,
            policyProgramQueryRepository = policyProgramQueryRepository,
            cursorCodec = cursorCodec,
        )
    }

    @Test
    fun `searchRecords delegates keyword condition to farming record service with count`() {
        val condition = recordCondition(keyword = "황기", cursor = "cursor-1", size = 10)
        val summary = recordSummary()
        `when`(farmingRecordService.search(condition))
            .thenReturn(FarmingRecordResult.Page(items = listOf(summary), nextCursor = "cursor-2"))
        `when`(farmingRecordService.count(condition)).thenReturn(25L)

        val page = service.searchRecords(SearchQuery(memberId = memberId, keyword = "황기", cursor = "cursor-1", size = 10))

        assertThat(page.items).containsExactly(summary)
        assertThat(page.nextCursor).isEqualTo("cursor-2")
        assertThat(page.totalCount).isEqualTo(25)
    }

    @Test
    fun `searchPosts delegates latest-sorted keyword condition to community post service with count`() {
        val condition = postCondition(keyword = "황기", cursor = null, size = 20)
        val summary = postSummary()
        `when`(communityPostService.search(condition))
            .thenReturn(CommunityPostResult.Page(items = listOf(summary), nextCursor = null))
        `when`(communityPostService.count(condition)).thenReturn(1L)

        val page = service.searchPosts(SearchQuery(memberId = memberId, keyword = "황기", cursor = null, size = 20))

        assertThat(page.items).containsExactly(summary)
        assertThat(page.nextCursor).isNull()
        assertThat(page.totalCount).isEqualTo(1)
    }

    @Test
    fun `searchPolicies maps programs and encodes next cursor only when an extra row exists`() {
        val programs = (1..3).map { index ->
            policyProgram(title = "정책 $index", createdAt = createdAt.minusDays(index.toLong()))
        }
        `when`(
            policyProgramQueryRepository.search(
                PolicyProgramQueryRepository.SearchCondition(
                    keyword = "정책",
                    cursorCreatedAt = null,
                    cursorId = null,
                    size = 3,
                )
            )
        ).thenReturn(programs)
        `when`(policyProgramQueryRepository.count("정책")).thenReturn(10L)

        val page = service.searchPolicies(SearchQuery(memberId = memberId, keyword = "정책", cursor = null, size = 2))

        assertThat(page.items).hasSize(2)
        assertThat(page.items.map { it.title }).containsExactly("정책 1", "정책 2")
        assertThat(page.totalCount).isEqualTo(10)
        val payload = cursorCodec.decode(requireNotNull(page.nextCursor), PolicySearchCursorPayload::class.java)
        assertEquals(programs[1].createdAt, payload.createdAt)
        assertEquals(programs[1].id, payload.id)
    }

    @Test
    fun `searchPolicies returns null next cursor when rows fit in one page`() {
        val program = policyProgram(title = "정책", createdAt = createdAt)
        `when`(
            policyProgramQueryRepository.search(
                PolicyProgramQueryRepository.SearchCondition(
                    keyword = null,
                    cursorCreatedAt = null,
                    cursorId = null,
                    size = 21,
                )
            )
        ).thenReturn(listOf(program))
        `when`(policyProgramQueryRepository.count(null)).thenReturn(1L)

        val page = service.searchPolicies(SearchQuery(memberId = memberId, keyword = null, cursor = null, size = 20))

        assertThat(page.items.single().title).isEqualTo("정책")
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `searchPolicies decodes cursor into created at and id condition`() {
        val cursorProgram = policyProgram(title = "커서 기준", createdAt = createdAt)
        val cursor = cursorCodec.encode(
            PolicySearchCursorPayload(createdAt = cursorProgram.createdAt, id = requireNotNull(cursorProgram.id))
        )
        `when`(
            policyProgramQueryRepository.search(
                PolicyProgramQueryRepository.SearchCondition(
                    keyword = null,
                    cursorCreatedAt = cursorProgram.createdAt,
                    cursorId = cursorProgram.id,
                    size = 21,
                )
            )
        ).thenReturn(emptyList())
        `when`(policyProgramQueryRepository.count(null)).thenReturn(0L)

        val page = service.searchPolicies(SearchQuery(memberId = memberId, keyword = null, cursor = cursor, size = 20))

        assertThat(page.items).isEmpty()
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `searchPolicies throws for a malformed cursor`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.searchPolicies(SearchQuery(memberId = memberId, keyword = null, cursor = "%%%", size = 20))
        }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
    }

    @Test
    fun `searchPolicies rejects out-of-range page sizes`() {
        listOf(0, 51).forEach { size ->
            val exception = assertThrows(BusinessException::class.java) {
                service.searchPolicies(SearchQuery(memberId = memberId, keyword = null, cursor = null, size = size))
            }
            assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        }
    }

    @Test
    fun `searchAll assembles three preview sections with fixed size and no cursor`() {
        val recordConditionPreview = recordCondition(keyword = "황기", cursor = null, size = 3)
        val postConditionPreview = postCondition(keyword = "황기", cursor = null, size = 3)
        val summary = recordSummary()
        val post = postSummary()
        val program = policyProgram(title = "황기 재배 지원", createdAt = createdAt)
        `when`(farmingRecordService.search(recordConditionPreview))
            .thenReturn(FarmingRecordResult.Page(items = listOf(summary), nextCursor = "more"))
        `when`(farmingRecordService.count(recordConditionPreview)).thenReturn(12L)
        `when`(communityPostService.search(postConditionPreview))
            .thenReturn(CommunityPostResult.Page(items = listOf(post), nextCursor = null))
        `when`(communityPostService.count(postConditionPreview)).thenReturn(1L)
        `when`(
            policyProgramQueryRepository.search(
                PolicyProgramQueryRepository.SearchCondition(
                    keyword = "황기",
                    cursorCreatedAt = null,
                    cursorId = null,
                    size = 4,
                )
            )
        ).thenReturn(listOf(program))
        `when`(policyProgramQueryRepository.count("황기")).thenReturn(1L)

        val all = service.searchAll(memberId, "황기")

        assertThat(all.records.items).containsExactly(summary)
        assertThat(all.records.totalCount).isEqualTo(12)
        assertThat(all.policies.items.single().title).isEqualTo("황기 재배 지원")
        assertThat(all.policies.totalCount).isEqualTo(1)
        assertThat(all.posts.items).containsExactly(post)
        assertThat(all.posts.totalCount).isEqualTo(1)
    }

    private fun recordCondition(keyword: String?, cursor: String?, size: Int): FarmingRecordSearchCondition =
        FarmingRecordSearchCondition(
            memberId = memberId,
            startDate = null,
            endDate = null,
            keyword = keyword,
            cursor = cursor,
            size = size,
        )

    private fun postCondition(keyword: String?, cursor: String?, size: Int): CommunityPostSearchCondition =
        CommunityPostSearchCondition(
            memberId = memberId,
            cropId = null,
            postType = null,
            keyword = keyword,
            likedOnly = false,
            mineOnly = false,
            sort = CommunityPostSort.LATEST,
            cursor = cursor,
            size = size,
        )

    private fun recordSummary(): FarmingRecordResult.Summary = FarmingRecordResult.Summary(
        id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
        cropId = UUID.fromString("00000000-0000-0000-0000-000000000201"),
        cropName = "황기",
        workType = WorkType.HARVEST,
        workedAt = createdAt,
        weatherCondition = "맑음",
        weatherTemperature = 23,
        memoPreview = "수확 완료",
        thumbnailUrl = null,
    )

    private fun postSummary(): CommunityPostResult.PostSummary = CommunityPostResult.PostSummary(
        id = UUID.fromString("00000000-0000-0000-0000-000000000301"),
        cropId = UUID.fromString("00000000-0000-0000-0000-000000000201"),
        cropName = "황기",
        postType = CommunityPostType.GENERAL,
        title = "황기 수확 후기",
        bodyPreview = "올해 수확량이",
        thumbnailUrl = null,
        author = CommunityPostResult.AuthorSummary(
            memberId = memberId,
            nickname = "농부",
            profileImageUrl = null,
        ),
        commentCount = 2,
        likeCount = 5,
        likedByMe = false,
        createdAt = createdAt,
    )

    private fun policyProgram(title: String, createdAt: LocalDateTime): PolicyProgram {
        val program = PolicyProgram(
            id = UUID.randomUUID(),
            title = title,
            body = "정책 상세",
            region = "전국",
            targetManagementType = null,
        )
        BaseTimeEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(program, createdAt)
        }
        return program
    }
}
