package com.chamchamcham.domain.coaching.reportfeedback

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
        entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
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
    fun `pending feedback belongs to one report work type`() {
        val feedback = ReportFeedback.pending(member, report, WorkType.WATERING)

        assertThat(feedback.workType).isEqualTo(WorkType.WATERING)
        assertThat(feedback.report).isSameAs(report)
        assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.PENDING)
    }

    @Test
    fun `ready feedback stores one work type summary and ordered items without a section count cap`() {
        val feedback = ReportFeedback.pending(member, report, WorkType.WATERING)

        feedback.markReady(
            summary = "이번 관수 작업은 간격을 안정적으로 유지했어요.",
            items = listOf(
                item(ReportFeedbackItemSection.COMPARISON, "직전보다 기록 1회 증가", "직전 재배보다 물 주기 기록이 한 번 늘었어요."),
                item(ReportFeedbackItemSection.STRENGTH, "관수 4회", "관수를 네 번 기록해 흐름을 확인하기 좋았어요."),
                item(ReportFeedbackItemSection.IMPROVEMENT, "관수량 누락", "다음에는 관수량도 함께 기록하세요."),
                item(ReportFeedbackItemSection.NEXT_ACTION, "다음 관수", "관수 전에 토양 수분을 확인하세요."),
                item(ReportFeedbackItemSection.NEXT_ACTION, "관수 직후", "관수량과 토양 상태를 함께 기록하세요."),
            ),
            citations = listOf(mapOf("id" to "report:current")),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "test-chat",
            embeddingModel = "test-embedding",
        )

        assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(feedback.summary).isEqualTo("이번 관수 작업은 간격을 안정적으로 유지했어요.")
        assertThat(feedback.items().map(ReportFeedbackItem::displayOrder)).containsExactly(0, 1, 2, 3, 4)
        assertThat(feedback.items().map(ReportFeedbackItem::section))
            .containsExactly(
                ReportFeedbackItemSection.COMPARISON,
                ReportFeedbackItemSection.STRENGTH,
                ReportFeedbackItemSection.IMPROVEMENT,
                ReportFeedbackItemSection.NEXT_ACTION,
                ReportFeedbackItemSection.NEXT_ACTION,
            )
    }

    @Test
    fun `ready feedback permits a summary without forced items`() {
        val feedback = ReportFeedback.pending(member, report, WorkType.WATERING)

        feedback.markReady(
            summary = "이번 관수 기록의 흐름을 확인했어요.",
            items = emptyList(),
            citations = emptyList(),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "test-chat",
            embeddingModel = "test-embedding",
        )

        assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(feedback.items()).isEmpty()
    }

    @Test
    fun `ready feedback rejects blank summary and blank item values`() {
        assertThatThrownBy {
            ReportFeedback.pending(member, report, WorkType.WATERING).markReady(
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
            ReportFeedback.pending(member, report, WorkType.WATERING).markReady(
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

    @Test
    fun `failed feedback can retry with its previous input cleared`() {
        val feedback = ReportFeedback.pending(member, report, WorkType.WATERING)
        feedback.attachInputSnapshot(mapOf("schemaVersion" to 3))
        feedback.markFailed("STRUCTURED_OUTPUT_INVALID")

        feedback.retry()

        assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(feedback.failureCode).isNull()
        assertThat(feedback.inputSnapshot).isNull()
    }

    private fun item(
        section: ReportFeedbackItemSection,
        basis: String,
        text: String,
    ) = ReportFeedbackItemDraft(section, basis, text)
}
