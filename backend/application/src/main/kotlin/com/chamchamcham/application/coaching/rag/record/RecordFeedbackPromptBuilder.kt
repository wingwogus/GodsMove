package com.chamchamcham.application.coaching.rag.record

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
        context: TodayRecordFeedbackContext,
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
            입력된 TodayRecordFeedbackContext와 검색된 공식문서 근거만 사용한다.
            사진은 분석하지 않는다. hasPhoto와 photoCount는 사진 첨부 여부로만 해석한다.
            의학적 효능, 복용법, 질병 치료 효과를 소비자 건강 조언처럼 말하지 않는다.
            면적, 등록 라벨, 희석 기준이 없으면 정확한 비료량이나 농약량을 invent하지 않는다.
            근거가 부족하면 riskLevel은 UNKNOWN, confidence는 0.3 이하로 둔다.
            귀농 청년도 바로 이해할 수 있는 쉬운 말로 짧고 구체적으로 쓴다.
            불확실한 판단은 단정하지 않는다. "~일 수 있습니다"보다 "기록만으로는 단정하기 어렵고, 먼저 확인하세요"처럼 쓴다.
            생육기간, 개화 후 일수, 수확 적기처럼 기준이 다른 값은 직접 비교하지 않는다.
            수확 후 가공, 건조, 저장 조언은 보조 점검 수준으로만 다룬다.
            예보는 확정된 날씨처럼 단정하지 않는다. "비가 올 예정이니"보다 "비 예보가 있으니"처럼 표현한다.
            forecast7Days에 강우, 고온, 고습, 건조, 강풍 신호가 있으면 nextActions에 예보 기반 점검 행동을 포함한다.
            권장사항은 확인, 기록, 비교, 라벨 확인처럼 보수적인 행동으로 작성한다.
            document-supported advice와 record/weather inference를 구분해서 설명한다.
            citationIds는 허용 citationIds에 명시된 값만 사용한다.
            당일 영농기록, 날씨, 최근 기록, 작업 통계를 근거로 삼을 때는 record citation id를 사용한다.
            공식문서를 근거로 삼을 때는 공식문서 근거의 [id]만 사용한다.
            summary, diagnosis, observations, recommendations, nextActions에는 chunkId나 UUID를 직접 쓰지 않는다.
            응답은 CoachingStructuredResult JSON schema만 따른다.
        """.trimIndent()
    }

    private fun userPrompt(
        context: TodayRecordFeedbackContext,
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

    private fun formatContext(context: TodayRecordFeedbackContext): String {
        val cycle = context.cropCycle
        val target = context.targetRecord
        val recordDay = context.weather.recordDay
        val recentWeather = context.weather.recent7Days

        return buildString {
            appendLine("- schemaVersion: ${context.schemaVersion}")
            appendLine("- feedbackRequestId: ${context.feedbackRequestId}")
            appendLine("- 농장: ${context.farm.name} (${context.farm.address})")
            appendLine(
                "- 회원: 영농 경력: ${context.member.experienceLevel ?: "미상"}, " +
                    "경영 형태: ${context.member.managementType ?: "미상"}"
            )
            appendLine("- 작물: ${context.crop.name} / 약용부위분류: ${context.crop.usePartCategory.recordFeedbackLabel()}")
            appendLine("- 작물주기: ${cycle?.daysAfterPlanting?.let { "${it}일차" } ?: "미상"}")
            appendLine("- 기록일: ${target.recordedOn}")
            appendLine("- 작업유형: ${target.workType.label}")
            appendLine("- 메모: ${target.memo}")
            appendLine("- 사진첨부: ${target.hasPhoto}, 사진수: ${target.photoCount}")
            appendLine("- 필드: ${formatMap(target.fields)}")
            appendLine(
                "- 당일 날씨: 평균 ${recordDay.avgTemperatureC ?: "미상"}C, " +
                    "최고 ${recordDay.maxTemperatureC ?: "미상"}C, " +
                    "최저 ${recordDay.minTemperatureC ?: "미상"}C, " +
                    "강수 ${recordDay.rainfallMm ?: "미상"}mm, " +
                    "습도 ${recordDay.humidityPct ?: "미상"}%"
            )
            appendLine(
                "- 최근 7일 강수량: ${recentWeather.rainfallMm ?: "미상"}mm, " +
                    "고온일수: ${recentWeather.hotDaysCount ?: "미상"}, " +
                    "건조일수: ${recentWeather.dryDaysCount ?: "미상"}"
            )
            appendLine("- 예보: ${formatForecast(context.weather.forecast7Days)}")
            appendLine("- 최근 기록: ${formatRecentRecords(context.recentRecords)}")
            appendLine("- 주기별 작업 횟수: ${formatMap(context.workTypeStats.cycleCounts)}")
            appendLine("- 유형별 마지막 작업일: ${formatMap(context.workTypeStats.lastWorkedOnByType)}")
            appendLine("- 최근 30일 작업 횟수: ${formatMap(context.workTypeStats.recent30DayCounts)}")
        }.trim()
    }

    private fun formatForecast(forecast: List<RecordFeedbackForecastDayWeather>): String {
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

    private fun formatRecentRecords(records: List<RecordFeedbackRecentRecordContext>): String {
        if (records.isEmpty()) {
            return "없음"
        }
        return records.joinToString(" | ") {
            "${it.recordedOn} ${it.workType.label}: ${it.memoSummary}"
        }
    }

    private fun formatEvidence(evidence: List<RecordFeedbackEvidence>): String {
        if (evidence.isEmpty()) {
            return "검색된 공식문서 근거 없음"
        }
        return evidence.joinToString("\n\n") {
            val page = it.page?.let { page -> " p.$page" } ?: ""
            "[${it.id}] ${it.title}$page\n${it.content}"
        }
    }

    private fun formatAllowedCitationIds(
        context: TodayRecordFeedbackContext,
        evidence: List<RecordFeedbackEvidence>
    ): String {
        return buildString {
            appendLine("- ${context.recordCitationId()} : 당일 영농기록 context")
            evidence.forEach {
                appendLine("- ${it.id} : ${it.title}")
            }
        }.trim()
    }

    private fun formatMap(map: Map<*, *>): String {
        if (map.isEmpty()) {
            return "없음"
        }
        return map.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}
