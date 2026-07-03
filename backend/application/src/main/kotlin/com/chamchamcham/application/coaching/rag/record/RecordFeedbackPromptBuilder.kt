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
            권장사항은 확인, 기록, 비교, 라벨 확인처럼 보수적인 행동으로 작성한다.
            document-supported advice와 record/weather inference를 구분해서 설명한다.
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
                    "강수 ${recordDay.rainfallMm ?: "미상"}mm, " +
                    "습도 ${recordDay.humidityPct ?: "미상"}%"
            )
            appendLine(
                "- 최근 7일 강수량: ${recentWeather.rainfallMm ?: "미상"}mm, " +
                    "고온일수: ${recentWeather.hotDaysCount ?: "미상"}, " +
                    "건조일수: ${recentWeather.dryDaysCount ?: "미상"}"
            )
            appendLine("- 최근 기록: ${formatRecentRecords(context.recentRecords)}")
            appendLine("- 주기별 작업 횟수: ${formatMap(context.workTypeStats.cycleCounts)}")
            appendLine("- 최근 30일 작업 횟수: ${formatMap(context.workTypeStats.recent30DayCounts)}")
        }.trim()
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

    private fun formatMap(map: Map<*, *>): String {
        if (map.isEmpty()) {
            return "없음"
        }
        return map.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}
