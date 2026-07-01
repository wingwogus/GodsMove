package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class FarmingRecordDocumentFactoryTest {
    @Test
    fun `build creates document content and metadata for farming record`() {
        val factory = FarmingRecordDocumentFactory()
        val record = IndexedFarmingRecord(
            recordId = UUID.fromString("40000000-0000-0000-0000-000000000001"),
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000042"),
            farmId = UUID.fromString("10000000-0000-0000-0000-000000000042"),
            cropId = UUID.fromString("20000000-0000-0000-0000-000000000042"),
            workTypeId = UUID.fromString("30000000-0000-0000-0000-000000000002"),
            memberName = "박민서",
            memberRegion = "강원특별자치도 평창군 진부면",
            farmName = "하늘들 약초농장",
            cropName = "참당귀",
            workTypeName = "관수",
            workedAt = LocalDateTime.of(2026, 6, 22, 7, 50),
            memo = "점적 관수를 진행했다.",
            fieldValues = listOf("관수 시간: 55분")
        )

        val document = factory.build(record)

        assertThat(document.text).contains("농업인: 박민서")
        assertThat(document.text).contains("영농일지: 점적 관수를 진행했다.")
        assertThat(document.metadata["label"]).isEqualTo("관수 2026-06-22")
        assertThat(document.metadata["memberId"]).isEqualTo(record.memberId.toString())
        assertThat(document.metadata["sourceType"]).isEqualTo("FARMING_RECORD")
    }
}
