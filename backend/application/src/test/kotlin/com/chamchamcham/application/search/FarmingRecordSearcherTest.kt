package com.chamchamcham.application.search

import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.domain.farming.WorkType
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
class FarmingRecordSearcherTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val workedAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @Mock private lateinit var farmingRecordService: FarmingRecordService

    private lateinit var searcher: FarmingRecordSearcher

    @BeforeEach
    fun setUp() {
        searcher = FarmingRecordSearcher(farmingRecordService)
    }

    @Test
    fun `category returns RECORD`() {
        assertThat(searcher.category()).isEqualTo(SearchCategory.RECORD)
    }

    @Test
    fun `search maps query to unfiltered keyword condition and normalizes items`() {
        `when`(
            farmingRecordService.search(
                FarmingRecordSearchCondition(
                    memberId = memberId,
                    cropIds = emptyList(),
                    workTypes = emptyList(),
                    startDate = null,
                    endDate = null,
                    keyword = "황기",
                    cursor = "cursor-1",
                    size = 10
                )
            )
        ).thenReturn(
            FarmingRecordResult.Page(
                items = listOf(
                    FarmingRecordResult.Summary(
                        id = recordId,
                        cropId = cropId,
                        cropName = "황기",
                        workType = WorkType.HARVEST,
                        workedAt = workedAt,
                        weatherCondition = "맑음",
                        weatherTemperature = 20,
                        memoPreview = "수확 완료",
                        thumbnailUrl = "https://example.test/1.jpg",
                    )
                ),
                nextCursor = "cursor-2"
            )
        )
        `when`(
            farmingRecordService.count(
                FarmingRecordSearchCondition(
                    memberId = memberId,
                    cropIds = emptyList(),
                    workTypes = emptyList(),
                    startDate = null,
                    endDate = null,
                    keyword = "황기",
                    cursor = "cursor-1",
                    size = 10
                )
            )
        ).thenReturn(7L)

        val page = searcher.search(
            SearchQuery(memberId = memberId, keyword = "황기", cursor = "cursor-1", size = 10)
        )

        assertThat(page.nextCursor).isEqualTo("cursor-2")
        assertThat(page.totalCount).isEqualTo(7L)
        val item = page.items.single()
        assertThat(item.category).isEqualTo(SearchCategory.RECORD)
        assertThat(item.id).isEqualTo(recordId)
        assertThat(item.title).isEqualTo("황기 · 수확")
        assertThat(item.snippet).isEqualTo("수확 완료")
        assertThat(item.thumbnailUrl).isEqualTo("https://example.test/1.jpg")
        assertThat(item.createdAt).isEqualTo(workedAt)
    }
}
