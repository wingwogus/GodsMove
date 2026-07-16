package com.chamchamcham.application.search

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramQueryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SearchService(
    private val farmingRecordService: FarmingRecordService,
    private val communityPostService: CommunityPostService,
    private val policyProgramQueryRepository: PolicyProgramQueryRepository,
    private val cursorCodec: OpaqueCursorCodec,
) {
    fun searchAll(memberId: UUID, keyword: String?): SearchResult.All {
        val preview = SearchQuery(memberId = memberId, keyword = keyword, cursor = null, size = PREVIEW_SIZE)
        return SearchResult.All(
            records = searchRecords(preview),
            policies = searchPolicies(preview),
            posts = searchPosts(preview),
        )
    }

    fun searchRecords(query: SearchQuery): SearchResult.RecordPage {
        val condition = FarmingRecordSearchCondition(
            memberId = query.memberId,
            startDate = null,
            endDate = null,
            keyword = query.keyword,
            cursor = query.cursor,
            size = query.size,
        )
        val page = farmingRecordService.search(condition)
        return SearchResult.RecordPage(
            items = page.items,
            nextCursor = page.nextCursor,
            totalCount = farmingRecordService.count(condition),
        )
    }

    fun searchPosts(query: SearchQuery): SearchResult.PostPage {
        val condition = CommunityPostSearchCondition(
            memberId = query.memberId,
            cropId = null,
            postType = null,
            keyword = query.keyword,
            likedOnly = false,
            mineOnly = false,
            sort = CommunityPostSort.LATEST,
            cursor = query.cursor,
            size = query.size,
        )
        val page = communityPostService.search(condition)
        return SearchResult.PostPage(
            items = page.items,
            nextCursor = page.nextCursor,
            totalCount = communityPostService.count(condition),
        )
    }

    fun searchPolicies(query: SearchQuery): SearchResult.PolicyPage {
        validatePageSize(query.size)
        val cursor = query.cursor?.let { cursorCodec.decode(it, PolicySearchCursorPayload::class.java) }
        val rows = policyProgramQueryRepository.search(
            PolicyProgramQueryRepository.SearchCondition(
                keyword = query.keyword,
                cursorCreatedAt = cursor?.createdAt,
                cursorId = cursor?.id,
                size = query.size + 1,
            )
        )
        val visible = rows.take(query.size)
        val nextCursor = if (rows.size > query.size) {
            val last = visible.last()
            cursorCodec.encode(PolicySearchCursorPayload(createdAt = last.createdAt, id = requireNotNull(last.id)))
        } else {
            null
        }
        return SearchResult.PolicyPage(
            items = visible.map(::toPolicyItem),
            nextCursor = nextCursor,
            totalCount = policyProgramQueryRepository.count(query.keyword),
        )
    }

    private fun toPolicyItem(program: PolicyProgram): SearchResult.PolicyItem = SearchResult.PolicyItem(
        id = requireNotNull(program.id) { "Persisted policy program id is required" },
        title = program.title,
        agencyName = program.agencyName,
        eligibilitySummary = program.eligibilitySummary,
        benefitSummary = program.benefitSummary,
        applicationPeriodLabel = program.applicationPeriodLabel,
        applyStartsOn = program.applyStartsOn,
        applyEndsOn = program.applyEndsOn,
        sourceUrl = program.sourceUrl,
        createdAt = program.createdAt,
    )

    private fun validatePageSize(size: Int) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private companion object {
        const val PREVIEW_SIZE = 3
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 50
    }
}
