package com.chamchamcham.application.coaching.reportfeedback.generation

import org.springframework.stereotype.Component

@Component
class ReportFeedbackPromptBuilder {
    fun build(
        context: ReportFeedbackContext,
        evidence: List<ReportFeedbackEvidence>,
    ): ReportFeedbackPrompt {
        val previous = context.previousReport?.let {
            "직전 완료 리포트: ${it.startsAt}~${it.endsAt}, 통계=${it.statistics}"
        } ?: "직전 완료 리포트 없음"
        val records = context.records.joinToString("\n") {
            "- record:${it.id} ${it.workedAt} ${it.workType} memo=${it.memo} details=${it.details}"
        }
        val documents = evidence.joinToString("\n") { "- ${it.id}: ${it.title} / ${it.content}" }
        return ReportFeedbackPrompt(
            system = """
                당신은 약용작물 재배 회고 코치다. 제공된 근거에 없는 수치나 사실을 만들지 않는다.
                summary, strengths, improvements, nextCycleActions를 구조화해 응답한다.
                각 항목은 basis, text, evidenceRefs를 가져야 한다.
                기술 문서가 없으면 기술 권고를 억지로 만들지 말고 기록 근거의 코칭만 제공한다.
                같은 항목을 반복하지 말고, 다음 사이클 계획은 실행 방법이 드러나게 작성한다.
            """.trimIndent(),
            user = """
                대상 리포트: ${context.report.farmName} / ${context.report.cropName}
                기간: ${context.report.startsAt}~${context.report.endsAt}
                통계: ${context.report.statistics}
                $previous
                대상 기록:
                $records
                공식 기술 문서:
                ${documents.ifBlank { "없음" }}
            """.trimIndent(),
        )
    }
}
