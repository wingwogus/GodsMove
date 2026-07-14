package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.coaching.common.CoachingTextPolicy
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.math.BigDecimal

class RecordFeedbackPromptBuilderTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val builder = RecordFeedbackPromptBuilder()

    @Test
    fun `prompt includes safety rules for medicinal crop record feedback`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다."))
        )

        assertThat(prompt.system).contains("약용작물 영농기록 피드백")
        assertThat(prompt.system).contains("사진은 분석하지 않는다")
        assertThat(prompt.system).contains("의학적 효능")
        assertThat(prompt.system).contains("정확한 비료량이나 농약량을 invent하지 않는다")
        assertThat(prompt.system).contains("citationIds는 허용 citationIds에 명시된 값만 사용한다")
        assertThat(prompt.system).contains("귀농 청년도 바로 이해할 수 있는 쉬운 말")
        assertThat(prompt.system).contains("불확실한 판단은 단정하지 않는다")
        assertThat(prompt.system).contains("수확 후 가공, 건조, 저장 조언은 보조 점검 수준")
        assertThat(prompt.system).contains("예보는 확정된 날씨처럼 단정하지 않는다")
        assertThat(prompt.system).contains("잘한 점은 정확히 1개만 작성한다")
        assertThat(prompt.system).contains("다음 행동은 2~3개만 작성한다")
        assertThat(prompt.system).contains(
            "잘한 점 text는 \"<기록의 구체 행동>한 점은 잘했어요.\" 형식으로 작성한다.",
            "잘한 점에는 이유, 조언, 다음 행동을 덧붙이지 않는다.",
            "다음 행동 text는 농부가 바로 실행할 현장 작업만 한 문장으로 작성한다.",
            "모든 text는 친근한 존댓말로 끝낸다.",
            "다음 행동은 \"~하세요.\"처럼, 잘한 점은 \"~했어요.\"처럼 작성한다.",
            "현재 날씨, 예보, 기록 내용, 생육 상황, 행동의 이유와 효과를 text에 쓰지 않는다.",
            "전문용어와 추상 표현(관수, 배수 상태, 병해충 흔적, 생육 상태)을 쓰지 않는다.",
            "다음 행동에는 어디를, 무엇을, 어떻게 볼지 또는 할지를 포함한다.",
            "병해충 행동은 공식문서 근거에 있는 눈으로 확인할 수 있는 증상만 구체적으로 쓴다.",
            "basis와 evidenceRefs는 내부 검증용이다. text에 basis, 근거, citation id를 직접 쓰지 않는다.",
            "각 text는 15~25자를 목표로 하되, 최대 60자까지 허용한다.",
        )
        assertThat(prompt.system).contains("응답은 RecordFeedbackContent JSON schema만 따른다")
        assertThat(prompt.system).contains(CoachingTextPolicy.promptInstructions)
        assertThat(prompt.system).contains("goodPoint", "nextActions", "basis", "text", "evidenceRefs")
        assertThat(prompt.system).doesNotContain(
            "summary",
            "riskLevel",
            "diagnosis",
            "observations",
            "recommendations",
            "follow-up",
            "follow up",
        )
    }

    @Test
    fun `prompt translates target record and live weather without raw internal values`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다."))
        )

        assertThat(prompt.user).contains("작물: 참당귀")
        assertThat(prompt.user).contains("쓰는 부위: 뿌리와 껍질")
        assertThat(prompt.user).contains("작업 유형: 물 주기")
        assertThat(prompt.user).contains("오전 흙 표면이 말라 보여 점적 관수함.")
        assertThat(prompt.user).contains("2026-07-04", "비 22.0밀리미터", "비 올 확률 80퍼센트")
        assertThat(prompt.user).contains("물 준 양: 보통 양", "물을 준 방법: 호스로 조금씩 물을 줌")
        assertThat(prompt.user).contains("현재 날씨: 구름많음, 30도")
        assertThat(prompt.user).contains("허용 citationIds:")
        assertThat(prompt.user).contains("record:10000000-0000-0000-0000-000000000004 : 대상 영농기록 context")
        assertThat(prompt.user).contains("weather:current : 현재 날씨 context")
        assertThat(prompt.user).contains("weather:2026-07-04 : 예보 날씨 context")
        assertThat(prompt.user).contains("[doc-1] 농업기술길잡이 007 약용작물 p.123")
        assertThat(prompt.user).contains("영농 경력: 1년")
        assertThat(prompt.user).contains("최저 21.4도")
        assertThat(prompt.user).doesNotContain(
            "schemaVersion",
            "managementType",
            "NON_REGISTERED_FARMER",
            "irrigationAmount",
            "irrigationMethod",
            "NORMAL",
            "DRIP",
            "riskFlags",
            "HEAVY_RAIN",
            "HIGH_HUMIDITY",
            "source=",
            "FAKE_WEATHER_PORT",
            "crop_work_type",
        )
        assertThat(prompt.user).doesNotContain("최근 기록", "작물주기", "작업 횟수")
    }

    @Test
    fun `prompt translates every typed record detail without enum names`() {
        val base = readFixture("today-record-feedback-watering.json")
        val cases = listOf(
            PlantingFeedbackDetail(
                plantingMethod = PlantingMethod.SEED,
                seedAmount = BigDecimal("1250.0000"),
                seedAmountUnit = SeedAmountUnit.G,
                seedlingCount = null,
                seedlingUnit = null,
                propagationMethod = null,
            ) to listOf("심기: 씨앗을 심음", "씨앗 양: 1250.0000그램"),
            FertilizingFeedbackDetail(
                materialName = "유기질비료",
                amount = BigDecimal("2500.0000"),
                amountUnit = FertilizerAmountUnit.G,
                applicationMethod = FertilizingMethod.SOIL,
            ) to listOf(
                "거름 이름: 유기질비료",
                "거름 양: 2500.0000그램",
                "거름을 준 방법: 흙에 거름을 줌",
            ),
            PestControlFeedbackDetail(
                pesticideName = "가가방",
                pesticideAmount = BigDecimal("120.0000"),
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal("20.0000"),
                totalSprayAmountUnit = SprayAmountUnit.L,
                pestName = "점무늬병",
            ) to listOf(
                "약 이름: 가가방",
                "약 사용량: 120.0000밀리리터",
                "약을 섞은 물의 양: 20.0000리터",
            ),
            WeedingFeedbackDetail(WeedingMethod.HAND) to listOf("풀을 정리한 방법: 손으로 풀을 뽑음"),
            HarvestFeedbackDetail(
                harvestAmount = BigDecimal("82.0000"),
                amountUnknown = false,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 6,
                growthPeriodUnit = GrowthPeriodUnit.MONTH,
                isLastHarvest = true,
            ) to listOf(
                "수확량: 82.0000킬로그램",
                "수확한 부위: 뿌리와 껍질",
                "기른 곳: 밭에서 기름",
                "기른 기간: 6개월",
                "마지막 수확: 네",
            ),
        )

        cases.forEach { (detail, expected) ->
            val prompt = builder.build(
                context = base.copy(record = base.record.copy(detail = detail)),
                queries = emptyList(),
                evidence = emptyList(),
            )

            assertThat(prompt.user).contains(*expected.toTypedArray())
        }
    }

    @Test
    fun `prompt omits optional record details when they were not recorded`() {
        val base = readFixture("today-record-feedback-watering.json")
        val cases = listOf(
            WateringFeedbackDetail(null, null) to listOf("물 준 양", "물을 준 방법"),
            FertilizingFeedbackDetail(
                materialName = "유기질비료",
                amount = BigDecimal("250"),
                amountUnit = FertilizerAmountUnit.ML,
                applicationMethod = null,
            ) to listOf("거름을 준 방법"),
            PestControlFeedbackDetail(
                pesticideName = "가가방",
                pesticideAmount = BigDecimal("120"),
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal("20"),
                totalSprayAmountUnit = SprayAmountUnit.L,
                pestName = null,
            ) to listOf("관리 대상"),
            WeedingFeedbackDetail(null) to listOf("풀을 정리한 방법"),
            HarvestFeedbackDetail(
                harvestAmount = null,
                amountUnknown = true,
                medicinalPart = null,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = null,
                growthPeriodUnit = null,
                isLastHarvest = false,
            ) to listOf("수확량", "수확한 부위", "기른 기간"),
        )

        cases.forEach { (detail, omittedLabels) ->
            val prompt = builder.build(
                context = base.copy(record = base.record.copy(detail = detail)),
                queries = emptyList(),
                evidence = emptyList(),
            )

            assertThat(prompt.user)
                .doesNotContain(*omittedLabels.toTypedArray())
                .doesNotContain("미상", "모름")
        }
    }

    @Test
    fun `prompt excludes blank evidence id from official evidence and allowed citations`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(
                RecordFeedbackEvidence("   ", "공백 ID 문서", 7, "공백 ID 근거는 prompt citation으로 쓰면 안 된다."),
                RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다.")
            )
        )

        val allowedCitationSection = prompt.user.substringAfter("허용 citationIds:")
            .substringBefore("공식문서 근거:")
        val officialEvidenceSection = prompt.user.substringAfter("공식문서 근거:")

        assertThat(allowedCitationSection).contains("doc-1 : 농업기술길잡이 007 약용작물")
        assertThat(allowedCitationSection).doesNotContain("공백 ID 문서")
        assertThat(officialEvidenceSection).contains("[doc-1] 농업기술길잡이 007 약용작물 p.123")
        assertThat(officialEvidenceSection).doesNotContain("공백 ID 문서")
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }
}
