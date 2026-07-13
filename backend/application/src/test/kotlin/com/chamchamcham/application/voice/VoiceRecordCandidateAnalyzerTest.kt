package com.chamchamcham.application.voice

import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class VoiceRecordCandidateAnalyzerTest {
    @Test
    fun `모든 필드가 비어있으면 기본 필수값이 전부 누락으로 보고된다`() {
        val missing = VoiceRecordCandidateAnalyzer.missingFields(VoiceRecordCandidate())

        assertThat(missing).containsExactlyInAnyOrder("farmId", "cropId", "workType", "workedAt")
    }

    @Test
    fun `상세정보가 필요없는 workType은 detail 누락으로 보고하지 않는다`() {
        val candidate = baseCandidate(workType = WorkType.WATERING)

        val missing = VoiceRecordCandidateAnalyzer.missingFields(candidate)

        assertThat(missing).doesNotContain("detail")
    }

    @Test
    fun `기타 workType은 detail 누락으로 보고하지 않는다`() {
        val candidate = baseCandidate(workType = WorkType.ETC)

        val missing = VoiceRecordCandidateAnalyzer.missingFields(candidate)

        assertThat(missing).doesNotContain("detail")
    }

    @Test
    fun `시비는 상세정보가 없으면 detail 누락으로 보고한다`() {
        val candidate = baseCandidate(workType = WorkType.FERTILIZING)

        val missing = VoiceRecordCandidateAnalyzer.missingFields(candidate)

        assertThat(missing).contains("detail")
    }

    @Test
    fun `시비는 상세정보가 채워지면 detail이 누락되지 않는다`() {
        val candidate = baseCandidate(workType = WorkType.FERTILIZING).copy(
            fertilizing = FarmingRecordCommand.FertilizingDetail(
                materialName = "유박비료",
                amount = java.math.BigDecimal("10"),
                amountUnit = FertilizerAmountUnit.KG,
            )
        )

        val missing = VoiceRecordCandidateAnalyzer.missingFields(candidate)

        assertThat(missing).doesNotContain("detail")
    }

    private fun baseCandidate(workType: WorkType) = VoiceRecordCandidate(
        farmId = UUID.randomUUID(),
        cropId = UUID.randomUUID(),
        workType = workType,
        workedAt = java.time.LocalDateTime.now(),
        memo = "메모",
    )
}
