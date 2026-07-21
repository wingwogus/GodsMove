package com.chamchamcham.application.voice

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class VoiceSessionInstructionsTest {
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val now = LocalDateTime.of(2026, 7, 17, 9, 0)

    private fun build(pesticides: List<VoicePesticideOption> = emptyList()): String =
        VoiceSessionInstructions.build(
            farms = listOf(FarmOption(farmId, "약초농장")),
            cropsByFarm = mapOf(farmId.toString() to listOf(CropOption(cropId, "황기"))),
            pesticides = pesticides,
            now = now,
            maxRounds = 10,
            maxDurationSeconds = 300,
        )

    @Test
    fun `첫 안내에서 필수·선택 항목과 기본값을 먼저 알려주도록 지시한다`() {
        val instructions = build()

        assertThat(instructions).contains("한 번에 말해")
        assertThat(instructions).contains("현재 시각", "자동으로 입력")
    }

    @Test
    fun `대화 한도와 스스로 시간을 관리하는 규칙을 포함한다`() {
        val instructions = build()

        assertThat(instructions).contains("10번", "5분")
        assertThat(instructions).contains("스스로 시간을 관리")
        assertThat(instructions).doesNotContain("시간이 얼마 남지 않았습니다")
    }

    @Test
    fun `질문 전에 확인된 것과 남은 것을 요약하도록 지시한다`() {
        val instructions = build()

        assertThat(instructions).contains("지금까지 확인된 건")
        assertThat(instructions).contains("남은 건")
    }

    @Test
    fun `마무리 시 남은 항목은 이후 화면에서 채울 수 있다고 안내하도록 지시한다`() {
        val instructions = build()

        assertThat(instructions).contains("이후 화면에서 직접 채울 수 있다")
    }

    @Test
    fun `농약량 단위와 마지막 수확 여부를 필수로 안내한다`() {
        val instructions = build()

        assertThat(instructions).contains("농약량과 그 단위")
        assertThat(instructions).contains("마지막 수확")
    }

    @Test
    fun `memo는 30~500자 요약을 직접 작성하라고 지시한다`() {
        val instructions = build()

        assertThat(instructions).contains("30자 이상 500자 이내")
        assertThat(instructions).doesNotContain("간단히 요약")
    }

    @Test
    fun `농약 목록이 있으면 이름과 대상 병해충을 포함한다`() {
        val instructions = build(
            pesticides = listOf(VoicePesticideOption(name = "코니도(바이엘)", pests = listOf("진딧물", "총채벌레"))),
        )

        assertThat(instructions).contains("코니도(바이엘)")
        assertThat(instructions).contains("진딧물, 총채벌레")
    }

    @Test
    fun `농약 목록이 비어 있으면 농약 목록 섹션을 생략한다`() {
        assertThat(build()).doesNotContain("등록 농약 목록")
    }

    @Test
    fun `농지가 없으면 세션 중단 안내 문구를 유지한다`() {
        val instructions = VoiceSessionInstructions.build(
            farms = emptyList(),
            cropsByFarm = emptyMap(),
            pesticides = emptyList(),
            now = now,
            maxRounds = 10,
            maxDurationSeconds = 300,
        )

        assertThat(instructions).contains("등록된 농지가 없습니다")
    }

    @Test
    fun `필수값을 다 모아도 사용자에게 완료를 확인한 뒤 저장하도록 지시한다`() {
        val instructions = build()

        assertThat(instructions).contains("더 기록하거나 덧붙일 내용")
        assertThat(instructions).contains("사용자가 마쳤다고")
    }

    @Test
    fun `저장 전 확인 화면에서 검토·수정 후 저장됨을 안내하도록 지시한다`() {
        val instructions = build()

        assertThat(instructions).contains("확인 화면에서 검토")
        assertThat(instructions).contains("자동 저장되는 것이 아님")
    }

    @Test
    fun `작업 유형을 특정할 수 없으면 ETC로 분류하도록 지시한다`() {
        val instructions = build()

        assertThat(instructions).contains("특정할 수 없으면")
        assertThat(instructions).contains("ETC(기타)로 분류")
    }

    @Test
    fun `ETC로 분류해도 농지·작물·memo를 채워 save_farming_record를 호출하도록 지시한다`() {
        val instructions = build()

        // ETC를 분류만 하고 멈춰 tool 호출이 안 돼 후보(candidate)가 비던 버그 방지 회귀 테스트.
        assertThat(instructions).contains("ETC로 분류했더라도")
        assertThat(instructions).contains("save_farming_record를 반드시 호출")
    }

    @Test
    fun `못 처리한 예외를 사용자에게 인지시키는 규칙을 포함한다`() {
        val instructions = build()

        assertThat(instructions).contains("확인 화면에서 정확한 농약")
        assertThat(instructions).contains("한 기록에는 한 작업만")
    }
}
