package com.chamchamcham.application.coaching.chat

import com.chamchamcham.application.coaching.common.RagSourceType

enum class CoachingRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN
}

enum class CoachingPriority {
    HIGH,
    MEDIUM,
    LOW
}

enum class CoachingActionDue {
    IMMEDIATE,
    TODAY,
    THIS_WEEK,
    NEXT_CHECK
}

enum class CoachingRecordQualityScore {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

data class CoachingStructuredResult(
    val summary: String,
    val riskLevel: CoachingRiskLevel,
    val confidence: Double,
    val observations: List<CoachingObservation>,
    val diagnosis: String,
    val recommendations: List<CoachingRecommendation>,
    val nextActions: List<CoachingNextAction>,
    val followUpQuestions: List<String>,
    val citations: List<CoachingCitationRef>,
    val recordQuality: CoachingRecordQuality = CoachingRecordQuality(),
    val limitations: List<String> = emptyList()
) {
    companion object {
        fun insufficientEvidence(message: String): CoachingStructuredResult {
            return CoachingStructuredResult(
                summary = message,
                riskLevel = CoachingRiskLevel.UNKNOWN,
                confidence = 0.0,
                observations = emptyList(),
                diagnosis = message,
                recommendations = emptyList(),
                nextActions = emptyList(),
                followUpQuestions = listOf("최근 영농일지나 작물 상태 정보를 추가로 입력해주세요."),
                citations = emptyList(),
                recordQuality = CoachingRecordQuality(
                    score = CoachingRecordQualityScore.UNKNOWN,
                    missingOrWeakFields = emptyList(),
                    comment = "현재 근거만으로 기록 품질을 평가하기 어렵습니다."
                ),
                limitations = listOf("근거가 부족해 보수적으로 판단했습니다.")
            )
        }
    }
}

data class CoachingRecordQuality(
    val score: CoachingRecordQualityScore = CoachingRecordQualityScore.UNKNOWN,
    val missingOrWeakFields: List<String> = emptyList(),
    val comment: String = ""
)

data class CoachingObservation(
    val title: String,
    val detail: String,
    val citationIds: List<String>
)

data class CoachingRecommendation(
    val priority: CoachingPriority,
    val action: String,
    val reason: String,
    val caution: String? = null,
    val citationIds: List<String>
)

data class CoachingNextAction(
    val due: CoachingActionDue,
    val action: String,
    val citationIds: List<String>
)

data class CoachingCitationRef(
    val chunkId: String,
    val label: String,
    val sourceType: RagSourceType,
    // 아래는 서버가 벡터스토어 메타데이터에서 직접 채우는 authoritative 값 (LLM 생성 아님).
    val documentTitle: String? = null,
    val page: Int? = null,
    val source: String? = null
)
