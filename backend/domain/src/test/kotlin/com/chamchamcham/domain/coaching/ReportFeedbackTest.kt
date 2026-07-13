package com.chamchamcham.domain.coaching

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackTest {
    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초농장", roadAddress = "강원도 평창군")
    private val crop = Crop(
        id = UUID.randomUUID(),
        externalNo = 422,
        name = "황기",
        usePartCategory = CropUsePartCategory.ROOT_BARK,
    )
    private val finishedAt = LocalDateTime.of(2026, 7, 13, 9, 0)
    private val finalHarvest = FarmingRecord(
        id = UUID.randomUUID(),
        member = member,
        farm = farm,
        crop = crop,
        workType = WorkType.HARVEST,
        workedAt = finishedAt,
        weatherCondition = "맑음",
        weatherTemperature = 24,
        memo = "최종 수확",
        entryMode = "MANUAL",
    )
    private val report = FarmingCycleReport.create(
        member = member,
        farm = farm,
        crop = crop,
        projection = FarmingCycleReportProjection(
            status = FarmingCycleReportStatus.COMPLETED,
            startsAt = finishedAt.minusMonths(5),
            endsAt = finishedAt,
            startBasis = FarmingCycleStartBasis.FIRST_RECORD,
            finalHarvestRecord = finalHarvest,
            statisticsSchemaVersion = 1,
            statistics = CycleReportStatistics.empty(),
        ),
    )

    @Test
    fun `ready feedback stores summary and ordered items without a section count cap`() {
        val feedback = ReportFeedback.pending(member, report)

        feedback.markReady(
            summary = "이번 사이클은 관수 간격을 안정적으로 유지했습니다.",
            items = listOf(
                item(ReportFeedbackItemSection.STRENGTH, "관수 4회", "관수를 4회 기록해 건조 구간을 줄였습니다."),
                item(ReportFeedbackItemSection.IMPROVEMENT, "시비 1회", "시비 간격과 효과를 다음 사이클에 비교하세요."),
                item(ReportFeedbackItemSection.NEXT_CYCLE_ACTION, "파종 전", "파종 전 토양 상태를 기록하세요."),
                item(ReportFeedbackItemSection.NEXT_CYCLE_ACTION, "생육 중", "주간 관수 간격을 기록하세요."),
            ),
            citations = listOf(mapOf("id" to "report:current")),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "test-chat",
            embeddingModel = "test-embedding",
        )

        assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(feedback.summary).isEqualTo("이번 사이클은 관수 간격을 안정적으로 유지했습니다.")
        assertThat(feedback.items().map(ReportFeedbackItem::displayOrder)).containsExactly(0, 1, 2, 3)
        assertThat(feedback.items().map(ReportFeedbackItem::section))
            .containsExactly(
                ReportFeedbackItemSection.STRENGTH,
                ReportFeedbackItemSection.IMPROVEMENT,
                ReportFeedbackItemSection.NEXT_CYCLE_ACTION,
                ReportFeedbackItemSection.NEXT_CYCLE_ACTION,
            )
    }

    @Test
    fun `ready feedback rejects blank summary and blank item values`() {
        assertThatThrownBy {
            ReportFeedback.pending(member, report).markReady(
                summary = " ",
                items = listOf(item(ReportFeedbackItemSection.STRENGTH, "관수", "관수 기록이 좋습니다.")),
                citations = emptyList(),
                auditStatus = "PASS",
                auditWarnings = emptyList(),
                modelName = "test-chat",
                embeddingModel = "test-embedding",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            ReportFeedback.pending(member, report).markReady(
                summary = "요약",
                items = listOf(item(ReportFeedbackItemSection.STRENGTH, " ", "관수 기록이 좋습니다.")),
                citations = emptyList(),
                auditStatus = "PASS",
                auditWarnings = emptyList(),
                modelName = "test-chat",
                embeddingModel = "test-embedding",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun item(
        section: ReportFeedbackItemSection,
        basis: String,
        text: String,
    ) = ReportFeedbackItemDraft(section, basis, text)
}
