package com.chamchamcham.application.weather

import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Component

/**
 * 작업 유형별 날씨 조언. 우선순위가 있는 규칙 목록에서 처음 매치되는 규칙을 쓴다
 * (마지막 규칙은 항상 매치되는 fallback).
 */
@Component
class FarmingActivityAdvisor {

    fun advise(flags: WeatherFlags): List<FarmingAdvice> =
        WorkType.entries
            .filter { it != WorkType.ETC }
            .map { work ->
                val rule = rulesFor(work).first { it.matches(flags) }
                FarmingAdvice(workType = work, level = rule.level, message = rule.message)
            }

    private data class Rule(
        val level: AdviceLevel,
        val message: String,
        val matches: (WeatherFlags) -> Boolean
    )

    private fun rulesFor(work: WorkType): List<Rule> = when (work) {
        WorkType.PLANTING -> listOf(
            Rule(AdviceLevel.BAD, "비가 내려 심기에 적합하지 않아요. 그친 뒤로 미뤄주세요.") { it.raining },
            Rule(AdviceLevel.BAD, "기온이 낮아 어린 모종이 냉해를 입을 수 있어요. 심기를 미뤄주세요.") { it.frost },
            Rule(AdviceLevel.CAUTION, "한낮 더위가 심해요. 아침·저녁 선선할 때 심어주세요.") { it.heatWave },
            Rule(AdviceLevel.CAUTION, "바람이 강해요. 어린 모종이 흔들릴 수 있으니 주의하세요.") { it.windy },
            Rule(AdviceLevel.GOOD, "곧 비 소식이 있어 심고 나면 뿌리내림에 도움이 돼요.") { it.rainLikely },
            Rule(AdviceLevel.GOOD, "심기 좋은 날씨예요.") { it.mild },
            Rule(AdviceLevel.NORMAL, "심기에 무리 없는 날씨예요.") { true }
        )
        WorkType.WATERING -> listOf(
            Rule(AdviceLevel.BAD, "비가 내리고 있어 따로 물을 줄 필요가 없어요.") { it.raining },
            Rule(AdviceLevel.CAUTION, "곧 비 소식이 있어요. 관수는 비를 본 뒤 결정하세요.") { it.rainLikely },
            Rule(AdviceLevel.CAUTION, "폭염에는 한낮 관수를 피하고 아침·저녁에 충분히 주세요.") { it.heatWave },
            Rule(AdviceLevel.CAUTION, "공기가 건조해요. 물 마름이 빠를 수 있으니 관수량을 확인하세요.") { it.dry },
            Rule(AdviceLevel.NORMAL, "습도가 높아요. 과습하지 않도록 관수량을 줄여주세요.") { it.humid },
            Rule(AdviceLevel.NORMAL, "평소대로 관수하세요.") { true }
        )
        WorkType.FERTILIZING -> listOf(
            Rule(AdviceLevel.BAD, "비가 내리면 비료가 씻겨 내려가요. 그친 뒤에 주세요.") { it.raining },
            Rule(AdviceLevel.CAUTION, "곧 비가 올 수 있어요. 비료가 유실될 수 있으니 시기를 조절하세요.") { it.rainLikely },
            Rule(AdviceLevel.CAUTION, "폭염에는 비료 흡수 부담이 커요. 선선할 때 주세요.") { it.heatWave },
            Rule(AdviceLevel.CAUTION, "바람이 강해 입자·액상 비료가 날릴 수 있어요.") { it.windy },
            Rule(AdviceLevel.GOOD, "비료 주기 좋은 날씨예요.") { it.mild },
            Rule(AdviceLevel.NORMAL, "비료 주기에 무리 없는 날씨예요.") { true }
        )
        WorkType.PEST_CONTROL -> listOf(
            Rule(AdviceLevel.BAD, "비가 내리면 약제가 씻겨 효과가 떨어져요. 그친 뒤에 방제하세요.") { it.raining },
            Rule(AdviceLevel.CAUTION, "바람이 강하면 약제가 흩날려 위험하고 효과도 떨어져요. 잦아든 뒤 방제하세요.") { it.windy },
            Rule(AdviceLevel.CAUTION, "곧 비 소식이 있어요. 약효가 유지되기 어려우니 시기를 조절하세요.") { it.rainLikely },
            Rule(AdviceLevel.CAUTION, "한낮 고온엔 약해가 생길 수 있어요. 아침·저녁 선선할 때 방제하세요.") { it.heatWave },
            Rule(AdviceLevel.CAUTION, "습도가 높아 병해충이 번지기 쉬워요. 잎 상태를 함께 점검하세요.") { it.humid },
            Rule(AdviceLevel.GOOD, "방제하기 좋은 날씨예요.") { it.mild },
            Rule(AdviceLevel.NORMAL, "방제에 무리 없는 날씨예요.") { true }
        )
        WorkType.WEEDING -> listOf(
            Rule(AdviceLevel.CAUTION, "비가 내려 미끄러워요. 무리하지 말고 그친 뒤에 하세요.") { it.raining },
            Rule(AdviceLevel.CAUTION, "폭염에는 야외 작업이 위험해요. 아침·저녁 선선할 때 하세요.") { it.heatWave },
            Rule(AdviceLevel.CAUTION, "자외선이 매우 강해요. 모자·긴옷으로 햇빛을 가리고 작업하세요.") { it.highUv },
            Rule(AdviceLevel.GOOD, "잡초 관리하기 좋은 날씨예요.") { it.mild },
            Rule(AdviceLevel.NORMAL, "잡초 관리에 무리 없는 날씨예요.") { true }
        )
        WorkType.PRUNING -> listOf(
            Rule(AdviceLevel.CAUTION, "비가 내리면 자른 상처로 병균이 들어가기 쉬워요. 맑은 날 하세요.") { it.raining },
            Rule(AdviceLevel.CAUTION, "바람이 강해 가지가 흔들려 작업이 위험할 수 있어요.") { it.windy },
            Rule(AdviceLevel.CAUTION, "폭염에는 야외 작업이 힘들어요. 선선할 때 하세요.") { it.heatWave },
            Rule(AdviceLevel.CAUTION, "습도가 높으면 자른 부위로 병이 번지기 쉬워요. 도구 소독에 신경 쓰세요.") { it.humid },
            Rule(AdviceLevel.GOOD, "가지·순 정리하기 좋은 날씨예요.") { it.clearSky },
            Rule(AdviceLevel.NORMAL, "가지·순 정리에 무리 없는 날씨예요.") { true }
        )
        WorkType.HARVEST -> listOf(
            Rule(AdviceLevel.BAD, "비가 내리면 수확물이 젖어 무르거나 저장성이 떨어져요. 그친 뒤에 수확하세요.") { it.raining },
            Rule(AdviceLevel.CAUTION, "기온이 낮아 냉해 우려가 있어요. 서리 피해 전에 수확을 서두르세요.") { it.frost },
            Rule(AdviceLevel.CAUTION, "폭염엔 수확물이 빨리 시들어요. 아침 시원할 때 수확해 그늘에 보관하세요.") { it.heatWave },
            Rule(AdviceLevel.CAUTION, "곧 비 소식이 있어요. 비 오기 전에 수확을 마치는 게 좋아요.") { it.rainLikely },
            Rule(AdviceLevel.NORMAL, "습도가 높아 수확물이 무르기 쉬워요. 물기 없이 보관하세요.") { it.humid },
            Rule(AdviceLevel.GOOD, "수확하기 좋은 날씨예요.") { it.clearSky },
            Rule(AdviceLevel.NORMAL, "수확에 무리 없는 날씨예요.") { true }
        )
        WorkType.ETC -> listOf(Rule(AdviceLevel.NORMAL, "") { true })
    }
}
