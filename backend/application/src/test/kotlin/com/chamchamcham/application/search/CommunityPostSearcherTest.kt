package com.chamchamcham.application.search

import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CommunityPostSearcherTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @Mock private lateinit var communityPostService: CommunityPostService

    private lateinit var searcher: CommunityPostSearcher

    @BeforeEach
    fun setUp() {
        searcher = CommunityPostSearcher(communityPostService)
    }

    @Test
    fun `category returns POST`() {
        assertThat(searcher.category()).isEqualTo(SearchCategory.POST)
    }

    @Test
    fun `search maps query to latest sorted unfiltered condition and normalizes items`() {
        `when`(
            communityPostService.search(
                CommunityPostSearchCondition(
                    memberId = memberId,
                    cropId = null,
                    postType = null,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LATEST,
                    cursor = "cursor-1",
                    size = 10
                )
            )
        ).thenReturn(
            CommunityPostResult.Page(
                items = listOf(
                    CommunityPostResult.PostSummary(
                        id = postId,
                        cropId = cropId,
                        cropName = "황기",
                        postType = CommunityPostType.QUESTION,
                        title = "발아율 질문",
                        bodyPreview = "발아가 잘 안 됩니다",
                        thumbnailUrl = "https://example.test/1.jpg",
                        author = CommunityPostResult.AuthorSummary(memberId = memberId, nickname = "농부", profileImageUrl = null),
                        commentCount = 3,
                        likeCount = 1,
                        likedByMe = false,
                        createdAt = createdAt
                    )
                ),
                nextCursor = "cursor-2"
            )
        )

        val page = searcher.search(
            SearchQuery(memberId = memberId, keyword = "발아", cursor = "cursor-1", size = 10)
        )

        assertThat(page.nextCursor).isEqualTo("cursor-2")
        val item = page.items.single()
        assertThat(item.category).isEqualTo(SearchCategory.POST)
        assertThat(item.id).isEqualTo(postId)
        assertThat(item.title).isEqualTo("발아율 질문")
        assertThat(item.snippet).isEqualTo("발아가 잘 안 됩니다")
        assertThat(item.createdAt).isEqualTo(createdAt)
    }
}
