package com.chamchamcham.application.coaching.recordfeedback.generation

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
            다음 행동은 2~3개만 작성한다.
            각 항목은 basis, text, evidenceRefs를 반드시 가진다.
            각 text는 15~45자로 작성한다. 강제로 자르지 말고, 길이를 맞춰 다시 쓴다.
            basis에는 text 안에 그대로 들어갈 2글자 이상의 핵심 근거 단어를 포함한다.
            nextActions의 due는 TODAY, THIS_WEEK, NEXT_WEEK, NEXT_CHECK 중 하나만 사용한다.
            nextActions의 category는 WEATHER, PEST_DISEASE, IRRIGATION, FERTILIZING, PEST_CONTROL, HARVEST, CULTIVATION, GENERAL 중 하나만 사용한다.
            날씨 행동은 weather:current 또는 weather:<forecast-date> 근거가 있을 때만 작성한다.
            병해충 행동은 공식문서 근거가 있을 때만 작성한다.
            행동은 확인, 기록, 비교, 라벨 확인처럼 보수적인 행동으로 작성한다.
            citationIds는 허용 citationIds에 명시된 값만 사용한다.
            대상 영농기록을 근거로 삼을 때는 record citation id를 사용한다.
            날씨 근거를 삼을 때는 weather:current 또는 weather:<forecast-date> citation id를 사용한다.
            공식문서를 근거로 삼을 때는 공식문서 근거의 [id]만 사용한다.
            농부에게 보여줄 text에는 chunkId나 UUID를 직접 쓰지 않는다.
            응답은 RecordFeedbackContent JSON schema만 따른다.
            JSON 최상위 필드는 goodPoint와 nextActions만 둔다.
        """.trimIndent()
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
            ${queries.joinToString("\n") { "- ${it.query} (${it.reason})" }}

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
            appendLine("- schemaVersion: ${context.schemaVersion}")
            appendLine("- 농장: ${context.farm.name} (${context.farm.roadAddress})")
            appendLine(
                "- 회원: 영농 경력: ${context.member.experienceLevel ?: "미상"}, " +
                    "경영 형태: ${context.member.managementType ?: "미상"}"
            )
            appendLine("- 작물: ${context.crop.name} / 약용부위분류: ${context.crop.usePartCategory.recordFeedbackLabel()}")
            appendLine("- 기록시각: ${record.workedAt}")
            appendLine("- 작업유형: ${record.workType.label}")
            appendLine("- 작업상세: ${formatDetail(record.detail)}")
            appendLine("- 메모: ${record.memo}")
            appendLine("- 사진수: ${record.photoCount}")
            appendLine("- 기록 당시 날씨: ${record.recordedWeatherCondition}, ${record.recordedTemperatureC}C")
            appendLine("- 현재 날씨: ${formatCurrentWeather(weather)}")
            appendLine("- 예보: ${formatForecast(weather?.forecastDays.orEmpty())}")
            appendLine("- 컨텍스트 경고: ${context.warnings.joinToString(", ").ifBlank { "없음" }}")
        }.trim()
    }

    private fun formatCurrentWeather(weather: RecordFeedbackLiveWeather?): String {
        val current = weather?.current ?: return "없음"
        return "${current.skyCondition}, ${current.temperatureC}C, 관측 ${current.observedAt}, source=${weather.source}"
    }

    private fun formatForecast(forecast: List<RecordFeedbackForecastDay>): String {
        if (forecast.isEmpty()) {
            return "없음"
        }
        return forecast.joinToString(" | ") {
            "${it.date} 강수 ${it.rainfallMm ?: "미상"}mm, " +
                "강수확률 ${it.rainProbabilityPct ?: "미상"}%, " +
                "최고 ${it.maxTemperatureC ?: "미상"}C, " +
                "최저 ${it.minTemperatureC ?: "미상"}C, " +
                "습도 ${it.humidityPct ?: "미상"}%, " +
                "풍속 ${it.windSpeedMs ?: "미상"}m/s, " +
                "riskFlags=${it.riskFlags.joinToString(",").ifBlank { "없음" }}"
        }
    }

    private fun formatDetail(detail: RecordFeedbackWorkDetail): String {
        return when (detail) {
            is PlantingFeedbackDetail -> listOf(
                "seedAmount=${detail.seedAmount ?: "미상"}",
                "seedAmountUnit=${detail.seedAmountUnit ?: "미상"}",
                "seedlingCount=${detail.seedlingCount ?: "미상"}",
                "seedlingUnit=${detail.seedlingUnit ?: "미상"}",
                "propagationMethod=${detail.propagationMethod}"
            ).joinToString(", ")

            is WateringFeedbackDetail -> listOf(
                "irrigationAmount=${detail.irrigationAmount ?: "미상"}",
                "irrigationMethod=${detail.irrigationMethod ?: "미상"}"
            ).joinToString(", ")

            is FertilizingFeedbackDetail -> listOf(
                "materialCategory=${detail.materialCategory}",
                "amount=${detail.amount}",
                "amountUnit=${detail.amountUnit}",
                "applicationMethod=${detail.applicationMethod ?: "미상"}"
            ).joinToString(", ")

            is PestControlFeedbackDetail -> listOf(
                "pesticideCategory=${detail.pesticideCategory}",
                "pesticideAmount=${detail.pesticideAmount}",
                "pesticideAmountUnit=${detail.pesticideAmountUnit}",
                "totalSprayAmount=${detail.totalSprayAmount}",
                "totalSprayAmountUnit=${detail.totalSprayAmountUnit}",
                "pestTarget=${detail.pestTarget ?: "미상"}"
            ).joinToString(", ")

            is WeedingFeedbackDetail -> "weedingMethod=${detail.weedingMethod ?: "미상"}"

            is HarvestFeedbackDetail -> listOf(
                "harvestAmountKg=${detail.harvestAmountKg ?: "미상"}",
                "amountUnknown=${detail.amountUnknown}",
                "medicinalPart=${detail.medicinalPart}",
                "harvestSource=${detail.harvestSource}",
                "growthPeriod=${detail.growthPeriod}",
                "growthPeriodUnit=${detail.growthPeriodUnit}",
                "isFinalHarvest=${detail.isFinalHarvest}"
            ).joinToString(", ")

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
