package com.chamchamcham.application.weather

import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FarmingActivityAdvisorTest {

    private val advisor = FarmingActivityAdvisor()

    private fun flags(
        raining: Boolean = false,
        rainLikely: Boolean = false,
        heatWave: Boolean = false,
        frost: Boolean = false,
        mild: Boolean = false,
        humid: Boolean = false,
        dry: Boolean = false,
        windy: Boolean = false,
        highUv: Boolean = false,
        clearSky: Boolean = false
    ): WeatherFlags = WeatherFlags(
        raining = raining,
        rainLikely = rainLikely,
        heatWave = heatWave,
        frost = frost,
        mild = mild,
        humid = humid,
        dry = dry,
        windy = windy,
        highUv = highUv,
        clearSky = clearSky
    )

    @Test
    fun `ETC를 제외한 7개 작업을 WorkType 선언 순서대로 반환한다`() {
        val advices = advisor.advise(flags())

        assertThat(advices).hasSize(7)
        assertThat(advices.map { it.workType }).doesNotContain(WorkType.ETC)
        assertThat(advices.map { it.workType }).containsExactly(
            WorkType.PLANTING,
            WorkType.WATERING,
            WorkType.FERTILIZING,
            WorkType.PEST_CONTROL,
            WorkType.WEEDING,
            WorkType.PRUNING,
            WorkType.HARVEST
        )
    }

    @Test
    fun `비가 오면 심기와 수확은 BAD를 준다`() {
        val advices = advisor.advise(flags(raining = true))

        assertThat(advices.find { it.workType == WorkType.PLANTING }?.level).isEqualTo(AdviceLevel.BAD)
        assertThat(advices.find { it.workType == WorkType.HARVEST }?.level).isEqualTo(AdviceLevel.BAD)
    }

    @Test
    fun `모든 플래그가 false면 각 작업은 정확한 fallback 문구를 준다`() {
        val advices = advisor.advise(flags())

        val expected = mapOf(
            WorkType.PLANTING to ("심기에 무리 없는 날씨예요." to AdviceLevel.NORMAL),
            WorkType.WATERING to ("평소대로 관수하세요." to AdviceLevel.NORMAL),
            WorkType.FERTILIZING to ("비료 주기에 무리 없는 날씨예요." to AdviceLevel.NORMAL),
            WorkType.PEST_CONTROL to ("방제에 무리 없는 날씨예요." to AdviceLevel.NORMAL),
            WorkType.WEEDING to ("잡초 관리에 무리 없는 날씨예요." to AdviceLevel.NORMAL),
            WorkType.PRUNING to ("가지·순 정리에 무리 없는 날씨예요." to AdviceLevel.NORMAL),
            WorkType.HARVEST to ("수확에 무리 없는 날씨예요." to AdviceLevel.NORMAL)
        )

        expected.forEach { (workType, expectedMessageAndLevel) ->
            val advice = advices.find { it.workType == workType }
            assertThat(advice?.message).isEqualTo(expectedMessageAndLevel.first)
            assertThat(advice?.level).isEqualTo(expectedMessageAndLevel.second)
        }
    }

    @Test
    fun `WATERING은 어떤 조건에서도 GOOD을 주지 않는다`() {
        val allFlags = flags(
            raining = false, rainLikely = false, heatWave = false, frost = false,
            mild = true, humid = false, dry = false, windy = false, highUv = false, clearSky = true
        )

        val watering = advisor.advise(allFlags).find { it.workType == WorkType.WATERING }

        assertThat(watering?.level).isNotEqualTo(AdviceLevel.GOOD)
    }

    @Test
    fun `WEEDING은 highUv일 때 CAUTION을 준다`() {
        val weeding = advisor.advise(flags(highUv = true)).find { it.workType == WorkType.WEEDING }

        assertThat(weeding?.level).isEqualTo(AdviceLevel.CAUTION)
        assertThat(weeding?.message).isEqualTo("자외선이 매우 강해요. 모자·긴옷으로 햇빛을 가리고 작업하세요.")
    }

    @Test
    fun `PRUNING은 clearSky일 때 GOOD을 준다`() {
        val pruning = advisor.advise(flags(clearSky = true)).find { it.workType == WorkType.PRUNING }

        assertThat(pruning?.level).isEqualTo(AdviceLevel.GOOD)
        assertThat(pruning?.message).isEqualTo("가지·순 정리하기 좋은 날씨예요.")
    }
}
