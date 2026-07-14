package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.coaching.common.CoachingTextPolicy
import com.chamchamcham.application.coaching.common.toCoachingText
import org.springframework.stereotype.Component

data class RecordFeedbackEvidence(
    val id: String,
    val title: String,
    val page: Int?,
    val content: String
)

data class RecordFeedbackPrompt(
    val system: String,
    val user: String
)

@Component
class RecordFeedbackPromptBuilder {
    fun build(
        context: RecordFeedbackContext,
        queries: List<RecordFeedbackRetrievalQuery>,
        evidence: List<RecordFeedbackEvidence>
    ): RecordFeedbackPrompt {
        return RecordFeedbackPrompt(
            system = systemPrompt(),
            user = userPrompt(context, queries, evidence)
        )
    }

    private fun systemPrompt(): String {
        return """
            너는 약용작물 영농기록 피드백 보조자다.
            입력된 RecordFeedbackContext와 검색된 공식문서 근거만 사용한다.
            사진은 분석하지 않는다. photoCount는 사진 첨부 수로만 해석한다.
            의학적 효능, 복용법, 질병 치료 효과를 소비자 건강 조언처럼 말하지 않는다.
            면적, 등록 라벨, 희석 기준이 없으면 정확한 비료량이나 농약량을 invent하지 않는다.
            귀농 청년도 바로 이해할 수 있는 쉬운 말로 짧고 구체적으로 쓴다.
            불확실한 판단은 단정하지 않는다. "~일 수 있습니다"보다 "기록만으로는 단정하기 어렵고, 먼저 확인하세요"처럼 쓴다.
            생육기간, 개화 후 일수, 수확 적기처럼 기준이 다른 값은 직접 비교하지 않는다.
            수확 후 가공, 건조, 저장 조언은 보조 점검 수준으로만 다룬다.
            예보는 확정된 날씨처럼 단정하지 않는다. "비가 올 예정이니"보다 "비 예보가 있으니"처럼 표현한다.
            잘한 점은 정확히 1개만 작성한다.
            잘한 점 text는 "<기록의 구체 행동>한 점은 잘했어요." 형식으로 작성한다.
            잘한 점에는 이유, 조언, 다음 행동을 덧붙이지 않는다.
            다음 행동은 2~3개만 작성한다.
            다음 행동 text는 농부가 바로 실행할 현장 작업만 한 문장으로 작성한다.
            모든 text는 친근한 존댓말로 끝낸다.
            다음 행동은 "~하세요."처럼, 잘한 점은 "~했어요."처럼 작성한다.
            현재 날씨, 예보, 기록 내용, 생육 상황, 행동의 이유와 효과를 text에 쓰지 않는다.
            전문용어와 추상 표현(관수, 배수 상태, 병해충 흔적, 생육 상태)을 쓰지 않는다.
            "관수" 대신 "물을 주세요"처럼 일상말을 쓴다.
            다음 행동에는 어디를, 무엇을, 어떻게 볼지 또는 할지를 포함한다.
            병해충 행동은 공식문서 근거에 있는 눈으로 확인할 수 있는 증상만 구체적으로 쓴다.
            근거에 없는 병 이름, 증상, 약 이름을 만들지 않는다.
            기록 작성, 사진 촬영, 메모 작성은 기본 행동으로 추천하지 않는다.
            각 항목은 basis, text, evidenceRefs를 반드시 가진다.
            잘한 점 text는 15~23자를 목표로 작성한다.
            nextActions의 각 text는 15~25자를 목표로 작성한다.
            모든 text는 최대 60자까지 허용한다. 강제로 자르지 말고, 길이를 맞춰 다시 쓴다.
            basis와 evidenceRefs는 내부 검증용이다. text에 basis, 근거, citation id를 직접 쓰지 않는다.
            nextActions의 due는 TODAY, THIS_WEEK, NEXT_WEEK, NEXT_CHECK 중 하나만 사용한다.
            nextActions의 category는 WEATHER, PEST_DISEASE, IRRIGATION, FERTILIZING, PEST_CONTROL, HARVEST, CULTIVATION, GENERAL 중 하나만 사용한다.
            날씨 행동은 weather:current 또는 weather:<forecast-date> 근거가 있을 때만 작성한다.
            병해충 행동은 공식문서 근거가 있을 때만 작성한다.
            행동은 물 주기, 막힌 곳 치우기, 잎 살펴보기, 라벨 확인처럼 구체적으로 작성한다.
            citationIds는 허용 citationIds에 명시된 값만 사용한다.
            대상 영농기록을 근거로 삼을 때는 record citation id를 사용한다.
            날씨 근거를 삼을 때는 weather:current 또는 weather:<forecast-date> citation id를 사용한다.
            공식문서를 근거로 삼을 때는 공식문서 근거의 [id]만 사용한다.
            농부에게 보여줄 text에는 chunkId나 UUID를 직접 쓰지 않는다.
            응답은 RecordFeedbackContent JSON schema만 따른다.
            JSON 최상위 필드는 goodPoint와 nextActions만 둔다.
        """.trimIndent() + "\n" + CoachingTextPolicy.promptInstructions
    }

    private fun userPrompt(
        context: RecordFeedbackContext,
        queries: List<RecordFeedbackRetrievalQuery>,
        evidence: List<RecordFeedbackEvidence>
    ): String {
        return """
            피드백 대상 기록:
            ${formatContext(context)}

            검색 쿼리:
            ${queries.joinToString("\n") { "- ${it.query}" }}

            허용 citationIds:
            ${formatAllowedCitationIds(context, evidence)}

            공식문서 근거:
            ${formatEvidence(evidence)}
        """.trimIndent()
    }

    private fun formatContext(context: RecordFeedbackContext): String {
        val record = context.record
        val weather = context.weather

        return buildString {
            appendLine("- 농장: ${context.farm.name} (${context.farm.roadAddress})")
            appendLine("- 영농 경력: ${context.member.experienceLevel?.let { "${it}년" } ?: "미상"}")
            appendLine("- 작물: ${context.crop.name}")
            appendLine("- 쓰는 부위: ${context.crop.usePartCategory.toCoachingText()}")
            appendLine("- 기록 시각: ${record.workedAt}")
            appendLine("- 작업 유형: ${record.workType.toCoachingText()}")
            appendLine("- 작업 상세: ${formatDetail(record.detail)}")
            appendLine("- 메모: ${record.memo}")
            appendLine("- 사진 수: ${record.photoCount}장")
            appendLine("- 기록 당시 날씨: ${record.recordedWeatherCondition}, ${record.recordedTemperatureC}도")
            appendLine("- 현재 날씨: ${formatCurrentWeather(weather)}")
            appendLine("- 예보: ${formatForecast(weather?.forecastDays.orEmpty())}")
        }.trim()
    }

    private fun formatCurrentWeather(weather: RecordFeedbackLiveWeather?): String {
        val current = weather?.current ?: return "없음"
        return "${current.skyCondition}, ${current.temperatureC}도, ${current.observedAt} 관측"
    }

    private fun formatForecast(forecast: List<RecordFeedbackForecastDay>): String {
        if (forecast.isEmpty()) {
            return "없음"
        }
        return forecast.joinToString(" | ") {
            "${it.date} 비 ${it.rainfallMm ?: "미상"}밀리미터, " +
                "비 올 확률 ${it.rainProbabilityPct ?: "미상"}퍼센트, " +
                "최고 ${it.maxTemperatureC ?: "미상"}도, " +
                "최저 ${it.minTemperatureC ?: "미상"}도, " +
                "습도 ${it.humidityPct ?: "미상"}퍼센트, " +
                "바람 ${it.windSpeedMs ?: "미상"}미터/초"
        }
    }

    private fun formatDetail(detail: RecordFeedbackWorkDetail): String {
        return when (detail) {
            is PlantingFeedbackDetail -> buildList {
                add("심기: ${detail.plantingMethod.toCoachingText()}")
                detail.seedAmount?.let { amount ->
                    add("씨앗 양: $amount${detail.seedAmountUnit?.toCoachingText().orEmpty()}")
                }
                detail.seedlingCount?.let { count ->
                    add("모종 수: $count${detail.seedlingUnit?.toCoachingText().orEmpty()}")
                }
                detail.propagationMethod?.let { add("모종 만든 방법: ${it.toCoachingText()}") }
            }.joinToString(", ")

            is WateringFeedbackDetail -> buildList {
                detail.irrigationAmount?.let { add("물 준 양: ${it.toCoachingText()}") }
                detail.irrigationMethod?.let { add("물을 준 방법: ${it.toCoachingText()}") }
            }.joinToString(", ")

            is FertilizingFeedbackDetail -> buildList {
                add("거름 이름: ${detail.materialName}")
                add("거름 양: ${detail.amount}${detail.amountUnit.toCoachingText()}")
                detail.applicationMethod?.let { add("거름을 준 방법: ${it.toCoachingText()}") }
            }.joinToString(", ")

            is PestControlFeedbackDetail -> buildList {
                add("약 이름: ${detail.pesticideName}")
                add("약 사용량: ${detail.pesticideAmount}${detail.pesticideAmountUnit.toCoachingText()}")
                add("약을 섞은 물의 양: ${detail.totalSprayAmount}${detail.totalSprayAmountUnit.toCoachingText()}")
                detail.pestName?.let { add("관리 대상: $it") }
            }.joinToString(", ")

            is WeedingFeedbackDetail ->
                detail.weedingMethod?.let { "풀을 정리한 방법: ${it.toCoachingText()}" }.orEmpty()

            is HarvestFeedbackDetail -> buildList {
                detail.harvestAmount?.let { add("수확량: ${it}킬로그램") }
                detail.medicinalPart?.let { add("수확한 부위: ${it.toCoachingText()}") }
                add("기른 곳: ${detail.harvestSource.toCoachingText()}")
                detail.growthPeriod?.let { period ->
                    detail.growthPeriodUnit?.let { unit ->
                        add("기른 기간: $period${unit.toCoachingText()}")
                    }
                }
                add("마지막 수확: ${if (detail.isLastHarvest) "네" else "아니요"}")
            }.joinToString(", ")

            CommonFeedbackDetail -> "공통 상세 없음"
        }
    }

    private fun formatEvidence(evidence: List<RecordFeedbackEvidence>): String {
        val citableEvidence = evidence.filter { it.id.isNotBlank() }
        if (citableEvidence.isEmpty()) {
            return "검색된 공식문서 근거 없음"
        }
        return citableEvidence.joinToString("\n\n") {
            val page = it.page?.let { page -> " p.$page" } ?: ""
            "[${it.id}] ${it.title}$page\n${it.content}"
        }
    }

    private fun formatAllowedCitationIds(
        context: RecordFeedbackContext,
        evidence: List<RecordFeedbackEvidence>
    ): String {
        return buildString {
            appendLine("- ${context.recordCitationId()} : 대상 영농기록 context")
            if (context.weather != null) {
                appendLine("- weather:current : 현재 날씨 context")
                context.weather.forecastDays.forEach {
                    appendLine("- weather:${it.date} : 예보 날씨 context")
                }
            }
            evidence.filter { it.id.isNotBlank() }.forEach {
                appendLine("- ${it.id} : ${it.title}")
            }
        }.trim()
    }
}
