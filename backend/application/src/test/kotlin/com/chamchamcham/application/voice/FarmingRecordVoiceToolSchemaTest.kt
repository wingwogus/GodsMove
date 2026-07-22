package com.chamchamcham.application.voice

import com.chamchamcham.domain.farming.PesticideAmountUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class FarmingRecordVoiceToolSchemaTest {
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    private val schema = FarmingRecordVoiceToolSchema.build(
        farms = listOf(FarmOption(farmId, "약초농장")),
        cropsByFarm = mapOf(farmId.toString() to listOf(CropOption(cropId, "황기"))),
    )

    @Suppress("UNCHECKED_CAST")
    private fun parameters(): Map<String, Any?> = schema["parameters"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun properties(): Map<String, Any?> = parameters()["properties"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun detailProperties(name: String): Map<String, Any?> =
        (properties()[name] as Map<String, Any?>)["properties"] as Map<String, Any?>

    @Test
    fun `수확 상세에 isLastHarvest 필드가 boolean으로 존재한다`() {
        @Suppress("UNCHECKED_CAST")
        val isLastHarvest = detailProperties("harvest")["isLastHarvest"] as Map<String, Any?>?

        assertThat(isLastHarvest).isNotNull
        assertThat(isLastHarvest!!["type"]).isEqualTo("boolean")
        assertThat(isLastHarvest["description"] as String).contains("필수")
    }

    @Test
    fun `memo는 30~500자 요약을 직접 작성하라고 지시한다`() {
        @Suppress("UNCHECKED_CAST")
        val memo = properties()["memo"] as Map<String, Any?>
        val description = memo["description"] as String

        assertThat(description).contains("30", "500")
        assertThat(description).contains("직접 작성")
    }

    @Test
    fun `농약명은 목록 기반 이름 교정 규칙을 안내한다`() {
        @Suppress("UNCHECKED_CAST")
        val pesticideName = detailProperties("pestControl")["pesticideName"] as Map<String, Any?>

        assertThat(pesticideName["description"] as String).contains("비슷한 이름")
    }

    @Test
    fun `농약량 단위는 도메인 enum과 일치한다`() {
        @Suppress("UNCHECKED_CAST")
        val unit = detailProperties("pestControl")["pesticideAmountUnit"] as Map<String, Any?>

        assertThat(unit["enum"]).isEqualTo(PesticideAmountUnit.entries.map { it.name })
    }

    @Test
    fun `최상위 필수 필드는 변하지 않는다`() {
        assertThat(parameters()["required"]).isEqualTo(listOf("farmId", "cropId", "workType", "memo"))
    }

    @Test
    fun `상세 없는 유형은 필수 4개만으로 곧바로 호출하라고 안내한다`() {
        // ETC 등 상세 없는 유형에서 모델이 저장 도구를 끝내 호출하지 않던 버그 방지 회귀 테스트.
        val description = schema["description"] as String

        assertThat(description).contains("ETC")
        assertThat(description).contains("곧바로 이 도구를 호출")
    }
}
