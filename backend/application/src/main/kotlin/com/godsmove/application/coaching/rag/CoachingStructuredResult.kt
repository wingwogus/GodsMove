package com.godsmove.application.coaching.rag

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

data class CoachingStructuredResult(
    val summary: String,
    val riskLevel: CoachingRiskLevel,
    val confidence: Double,
    val observations: List<CoachingObservation>,
    val diagnosis: String,
    val recommendations: List<CoachingRecommendation>,
    val nextActions: List<CoachingNextAction>,
    val followUpQuestions: List<String>,
    val citations: List<CoachingCitationRef>
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
                citations = emptyList()
            )
        }
    }
}

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
    val sourceType: RagSourceType
)
