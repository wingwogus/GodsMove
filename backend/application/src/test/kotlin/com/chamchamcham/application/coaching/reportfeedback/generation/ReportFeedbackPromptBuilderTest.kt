package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.CoachingTextPolicy
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.Coverage
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
            .contains("nextActions는 다음 작업에서 언제 무엇을 어떻게 할지")
            .contains("summary와 모든 text는 친근한 존댓말로 끝낸다.")
            .contains("comparisons")
            .contains("서버가 계산한 비교값을 그대로 사용하고 다시 계산하지 않는다.")
            .contains("comparisons는 지난 재배와 달라진 사실만")
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
                "summary는 20~65자로 작성한다.",
                "comparisons의 text는 20~65자로 작성한다.",
                "strengths, improvements, nextActions의 text는 각각 20~65자로 작성한다.",
                "JSON을 반환하기 전에 공백과 문장부호를 포함한 summary와 모든 text의 글자 수가 20~65자인지 확인한다.",
                "최소 길이를 맞출 때 의미 없는 표현을 덧붙이지 말고 근거, 판단, 실행 방법을 보강해 다시 쓴다.",
                "65자를 넘으면 문장을 자르지 말고 핵심 내용을 남겨 다시 쓴다.",
            )
            .doesNotContain(
                "comparisons, strengths, improvements, nextActions는 근거가 없으면 빈 배열로 응답해도 된다.",
                "45~55자",
            )
        assertThat(prompt.user)
            .contains("대상 작업: 물 주기")
            .contains("기록 횟수: 4회")
            .contains("평균 작업 간격: 3.5일")
            .contains("물을 준 방법: 호스로 조금씩 물을 줌")
            .contains("지난 재배 기록 횟수: 3회")
            .contains("record:$recordId")
            .contains("report:$currentReportId")
            .contains("report:$previousReportId")
            .contains("지난 재배보다 기록 횟수가 1회 늘었어요. 변화율은 33퍼센트예요.")
            .contains("document-1")
            .contains("황기 관수 기술", "관수 후 토양 수분을 확인한다.")
            .doesNotContain(
                "직전",
                "33.33퍼센트",
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
    fun `prompt defines friendly and distinct roles for every public section`() {
        val prompt = ReportFeedbackPromptBuilder().build(context(), emptyList())

        assertThat(prompt.system).contains(
            "summary는 이번 재배에서 확인한 핵심을 작업과 기록 중심으로 균형 있게 요약한다.",
            "comparisons는 지난 재배와 달라진 사실만 설명하고 평가나 권고를 넣지 않는다.",
            "strengths는 근거에서 확인한 잘한 행동과 그 행동이 도움이 된 이유를 함께 설명한다.",
            "improvements는 부족한 점, 그 점이 판단이나 관리에 미친 영향, 앞으로 보완할 방향을 함께 설명한다.",
            "improvements는 작업 간격, 양, 방법, 시기처럼 실제 재배 행동에서 개선점을 먼저 찾는다.",
            "사진 첨부나 기록 누락 같은 기록 습관 이야기는 재배 행동에서 개선점을 찾을 수 없을 때만 사용한다.",
            "공식 기술 문서가 있으면 이번 작업 통계와 기록을 문서의 권장 방법에 비추어 개선 방향을 구체적으로 제시한다.",
            "자료가 부족해도 판단이 어렵거나 해석이 제한됐다는 설명으로 끝내지 않고, 지금 확인한 사실로 시도할 수 있는 재배 행동을 안내한다.",
            "nextActions는 다음 작업에서 언제 무엇을 어떻게 할지 실행 가능한 한 가지 재배 행동으로 작성한다.",
            "사용자에게 내부 보고서나 시스템을 설명하지 말고 농부가 남긴 작업과 기록을 먼저 말한다.",
            "공식 기술 문서를 근거로 사용해도 문서를 문장의 주어로 내세우지 않는다.",
            "다음 개선점 예시는 형식만 참고하고 내용을 복사하지 않는다.",
            "나쁜 예: \"정보가 없어 해석이 제한됐어요.\"",
            "나쁜 예: \"사진이 없어 상태를 알기 어려웠어요. 다음에는 사진을 남겨 보세요.\"",
            "좋은 예: \"물 주는 간격이 중간에 크게 벌어졌어요. 생육 초기에는 간격을 일정하게 지켜 보세요.\"",
            "summary, comparisons, strengths는 \"~했어요.\"처럼 회고형 존댓말로 작성한다.",
            "improvements는 부족한 점을 부드럽게 설명하고 보완 방향은 \"~해 보세요.\"처럼 제안한다.",
            "nextActions는 \"~하세요.\"처럼 분명한 행동형 존댓말로 작성한다.",
        )
        assertThat(prompt.system).doesNotContain(
            "다음에 함께 남길 기록 항목을 안내한다.",
            "언제 무엇을 기록하거나 확인할지",
            "좋은 예: \"기록에 필요한 정보가 빠져 판단하기 어려웠어요. 다음에는 빠진 정보도 함께 기록해 보세요.\"",
        )
    }

    @Test
    fun `comparison percentage uses half up rounding without decimals`() {
        listOf(
            BigDecimal("33.33") to "33",
            BigDecimal("33.50") to "34",
            BigDecimal("0.49") to "0",
        ).forEach { (rawPercentage, expectedPercentage) ->
            val base = context()
            val comparison = base.comparisons.single().copy(relativeChangePct = rawPercentage)

            val prompt = ReportFeedbackPromptBuilder().build(
                base.copy(comparisons = listOf(comparison)),
                emptyList(),
            )

            assertThat(prompt.user)
                .contains("변화율은 ${expectedPercentage}퍼센트예요.")
                .doesNotContain("변화율은 ${rawPercentage.toPlainString()}퍼센트예요.")
        }
    }

    @Test
    fun `comparison keeps decrease direction while rounding the absolute percentage`() {
        val base = context()
        val comparison = base.comparisons.single().copy(
            difference = BigDecimal("-1"),
            relativeChangePct = BigDecimal("-12.50"),
        )

        val prompt = ReportFeedbackPromptBuilder().build(
            base.copy(comparisons = listOf(comparison)),
            emptyList(),
        )

        assertThat(prompt.user)
            .contains("지난 재배보다 기록 횟수가 1회 줄었어요. 변화율은 13퍼센트예요.")
    }

    @Test
    fun `comparison omits unavailable percentage instead of explaining the calculation limit`() {
        val base = context()
        val comparison = base.comparisons.single().copy(relativeChangePct = null)

        val prompt = ReportFeedbackPromptBuilder().build(
            base.copy(comparisons = listOf(comparison)),
            emptyList(),
        )

        assertThat(prompt.user)
            .contains("지난 재배보다 기록 횟수가 1회 늘었어요.")
            .doesNotContain("변화율")
    }

    @Test
    fun `comparison coverage calls the prior period last cultivation`() {
        val base = context()
        val comparison = base.comparisons.single().copy(
            currentCoverage = Coverage(recordedCount = 3, targetCount = 4),
            previousCoverage = Coverage(recordedCount = 2, targetCount = 3),
        )

        val prompt = ReportFeedbackPromptBuilder().build(
            base.copy(comparisons = listOf(comparison)),
            emptyList(),
        )

        assertThat(prompt.user)
            .contains("입력 범위는 이번 3/4건, 지난 재배 2/3건이에요.")
            .doesNotContain("직전")
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
