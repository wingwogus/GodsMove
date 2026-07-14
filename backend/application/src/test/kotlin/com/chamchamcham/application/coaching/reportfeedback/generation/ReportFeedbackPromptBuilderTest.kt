package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.CoachingTextPolicy
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackPromptBuilderTest {
    private val recordId = UUID.randomUUID()
    private val currentReportId = UUID.randomUUID()
    private val previousReportId = UUID.randomUUID()

    @Test
    fun `prompt scopes instructions statistics and allowed evidence to one work type`() {
        val prompt = ReportFeedbackPromptBuilder().build(
            context = context(),
            evidence = listOf(
                ReportFeedbackEvidence(
                    id = "document-1",
                    title = "황기 관수 기술",
                    content = "관수 후 토양 수분을 확인한다.",
                ),
            ),
        )

        assertThat(prompt.system)
            .contains("대상 작업 타입 하나만")
            .contains("nextActions")
            .contains("빈 배열")
            .contains("선택한 작업의 다음 행동은 실행 방법이 드러나게 작성한다.")
            .contains("summary와 모든 text는 친근한 존댓말로 끝낸다.")
            .contains("comparisons")
            .contains("서버가 계산한 비교값을 그대로 사용하고 다시 계산하지 않는다.")
            .contains("comparisons에는 변화 사실만")
            .contains("comparison, strength, improvement, next-action 사이에 같은 내용을 반복하지 않는다.")
            .contains(CoachingTextPolicy.promptInstructions)
            .doesNotContain("다음 사이클 계획")
        assertThat(prompt.system)
            .contains(
                "strengths, improvements, nextActions는 각각 정확히 1개의 항목으로 응답한다.",
                "서버가 계산한 비교값이 있으면 comparisons는 정확히 1개의 항목으로 응답한다.",
                "서버가 계산한 비교값이 없으면 comparisons는 반드시 빈 배열로 응답한다.",
                "각 배열 항목의 text는 줄바꿈이나 목록 기호 없이 하나의 문단으로 작성한다.",
                "한 문단 안에는 자연스럽게 이어지는 여러 문장을 작성해도 된다.",
            )
            .doesNotContain(
                "comparisons, strengths, improvements, nextActions는 근거가 없으면 빈 배열로 응답해도 된다.",
            )
        assertThat(prompt.user)
            .contains("대상 작업: 물 주기")
            .contains("기록 횟수: 4회")
            .contains("평균 작업 간격: 3.5일")
            .contains("물을 준 방법: 호스로 조금씩 물을 줌")
            .contains("직전 기록 횟수: 3회")
            .contains("record:$recordId")
            .contains("report:$currentReportId")
            .contains("report:$previousReportId")
            .contains("직전보다 기록 횟수가 1회 늘었고 변화율은 33.33퍼센트")
            .contains("document-1")
            .contains("황기 관수 기술", "관수 후 토양 수분을 확인한다.")
            .doesNotContain(
                "WATERING",
                "recordCount",
                "averageIntervalDays",
                "details=",
                "DRIP",
                "LOW",
                "code=",
                "label=",
                "CategoryRef",
            )
            .doesNotContain("FERTILIZING")
            .doesNotContain("수확량")
    }

    @Test
    fun `unknown enum code is omitted instead of falling back to raw code or label`() {
        val context = context().copy(
            report = context().report.copy(
                statistics = mapOf(
                    "recordCount" to 1,
                    "methodDistribution" to listOf(
                        mapOf("code" to "NEW_METHOD", "label" to "새 방식", "count" to 1, "ratePct" to 100),
                    ),
                ),
            ),
            records = context().records.map {
                it.copy(
                    details = mapOf(
                        "watering" to mapOf(
                            "method" to mapOf("code" to "NEW_METHOD", "label" to "새 방식"),
                        ),
                    ),
                )
            },
        )

        val prompt = ReportFeedbackPromptBuilder().build(context, emptyList())

        assertThat(prompt.user).doesNotContain("NEW_METHOD", "새 방식")
    }

    @Test
    fun `prompt requires an empty comparison section when the server has no comparison`() {
        val context = context().copy(previousReport = null, comparisons = emptyList())

        val prompt = ReportFeedbackPromptBuilder().build(context, emptyList())

        assertThat(prompt.system)
            .contains("서버가 계산한 비교값이 없으면 comparisons는 반드시 빈 배열로 응답한다.")
    }

    @Test
    fun `planting statistics include seed and seedling as easy planting descriptions`() {
        val base = context()
        val plantingContext = base.copy(
            workType = WorkType.PLANTING,
            report = base.report.copy(
                statistics = mapOf(
                    "recordCount" to 2,
                    "propagationMethods" to listOf(
                        mapOf("code" to "SEED", "recordCount" to 1),
                        mapOf("code" to "SEEDLING", "recordCount" to 1),
                    ),
                ),
            ),
            records = emptyList(),
            previousReport = null,
        )

        val prompt = ReportFeedbackPromptBuilder().build(plantingContext, emptyList())

        assertThat(prompt.user)
            .contains("심은 방법: 씨앗을 심음 1회")
            .contains("심은 방법: 모종을 심음 1회")
            .doesNotContain("SEED", "SEEDLING")
    }

    @Test
    fun `fertilizer statistics keep liquid fertilizer names in the prompt`() {
        val base = context()
        val fertilizingContext = base.copy(
            workType = WorkType.FERTILIZING,
            report = base.report.copy(
                statistics = mapOf(
                    "recordCount" to 1,
                    "materialDistribution" to listOf(
                        mapOf("code" to "액상비료", "label" to "액상비료", "count" to 1),
                    ),
                ),
            ),
            records = emptyList(),
            previousReport = null,
        )

        val prompt = ReportFeedbackPromptBuilder().build(fertilizingContext, emptyList())

        assertThat(prompt.user).contains("거름 이름: 액상비료 1회")
    }

    @Test
    fun `pesticide statistics show the product label without exposing its id`() {
        val pesticideId = UUID.randomUUID()
        val base = context()
        val pestControlContext = base.copy(
            workType = WorkType.PEST_CONTROL,
            report = base.report.copy(
                statistics = mapOf(
                    "recordCount" to 1,
                    "categoryDistribution" to listOf(
                        mapOf("code" to pesticideId.toString(), "label" to "가가방", "count" to 1),
                    ),
                ),
            ),
            records = emptyList(),
            previousReport = null,
        )

        val prompt = ReportFeedbackPromptBuilder().build(pestControlContext, emptyList())

        assertThat(prompt.user)
            .contains("약 이름: 가가방 1회")
            .doesNotContain(pesticideId.toString())
    }

    @Test
    fun `harvest records distinguish cultivated crops from wild collection in easy language`() {
        val cultivatedPrompt = ReportFeedbackPromptBuilder().build(
            harvestContext(HarvestSource.CULTIVATED),
            emptyList(),
        )
        val foragedPrompt = ReportFeedbackPromptBuilder().build(
            harvestContext(HarvestSource.FORAGED),
            emptyList(),
        )

        assertThat(cultivatedPrompt.user)
            .contains("수확한 곳: 밭에서 기름")
            .doesNotContain("산이나 들에서 얻음", HarvestSource.CULTIVATED.name)
        assertThat(foragedPrompt.user)
            .contains("수확한 곳: 산이나 들에서 얻음")
            .doesNotContain("밭에서 기름", HarvestSource.FORAGED.name)
    }

    private fun harvestContext(harvestSource: HarvestSource): ReportFeedbackContext {
        val base = context()
        return base.copy(
            workType = WorkType.HARVEST,
            report = base.report.copy(statistics = mapOf("recordCount" to 1)),
            records = listOf(
                base.records.single().copy(
                    workType = WorkType.HARVEST,
                    details = mapOf(
                        "harvest" to mapOf(
                            "harvestSource" to mapOf(
                                "code" to harvestSource.name,
                                "label" to harvestSource.label,
                            ),
                        ),
                    ),
                ),
            ),
            previousReport = null,
        )
    }

    private fun context() = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = WorkType.WATERING,
        report = ReportFeedbackReport(
            id = currentReportId,
            farmName = "약초농장",
            cropName = "황기",
            startsAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            statistics = mapOf(
                "recordCount" to 4,
                "averageIntervalDays" to 3.5,
                "amountDistribution" to listOf(
                    mapOf("code" to "LOW", "label" to "적음", "count" to 2, "ratePct" to 50),
                ),
                "methodDistribution" to listOf(
                    mapOf("code" to "DRIP", "label" to "점적관수", "count" to 4, "ratePct" to 100),
                ),
            ),
        ),
        records = listOf(
            ReportFeedbackRecord(
                id = recordId,
                workedAt = LocalDateTime.of(2026, 4, 1, 9, 0),
                workType = WorkType.WATERING,
                memo = "점적관수를 했어요.",
                details = mapOf(
                    "weatherCondition" to "맑음",
                    "weatherTemperature" to 18,
                    "hasPhoto" to false,
                    "watering" to mapOf(
                        "amount" to mapOf("code" to "LOW", "label" to "적음"),
                        "method" to mapOf("code" to "DRIP", "label" to "점적관수"),
                    ),
                ),
            ),
        ),
        previousReport = ReportFeedbackPreviousReport(
            id = previousReportId,
            startsAt = LocalDateTime.of(2025, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2025, 7, 1, 9, 0),
            statistics = mapOf(
                "recordCount" to 3,
                "averageIntervalDays" to 4.0,
                "methodDistribution" to listOf(
                    mapOf("code" to "DRIP", "label" to "점적관수", "count" to 3, "ratePct" to 100),
                ),
            ),
        ),
        comparisons = listOf(
            ReportFeedbackComparison(
                metricKey = "recordCount",
                metricLabel = "기록 횟수",
                currentValue = BigDecimal("4"),
                previousValue = BigDecimal("3"),
                difference = BigDecimal("1"),
                relativeChangePct = BigDecimal("33.33"),
                unit = "회",
            ),
        ),
        warnings = emptyList(),
    )
}
