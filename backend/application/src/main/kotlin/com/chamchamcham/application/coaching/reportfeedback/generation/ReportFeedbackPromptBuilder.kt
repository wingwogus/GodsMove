package com.chamchamcham.application.coaching.reportfeedback.generation

import org.springframework.stereotype.Component

@Component
class ReportFeedbackPromptBuilder {
    fun build(
        context: ReportFeedbackContext,
        evidence: List<ReportFeedbackEvidence>,
    ): ReportFeedbackPrompt {
        val previous = context.previousReport?.let {
            "직전 완료 리포트(report:${it.id}): ${it.startsAt}~${it.endsAt}, 통계=${it.statistics}"
        } ?: "직전 완료 리포트 없음"
        val records = context.records.joinToString("\n") {
            "- record:${it.id} ${it.workedAt} ${it.workType} memo=${it.memo} details=${it.details}"
        }
        val documents = evidence.joinToString("\n") { "- ${it.id}: ${it.title} / ${it.content}" }
        val allowedEvidenceRefs = buildList {
            context.records.forEach { add("- record:${it.id} : 대상 영농기록") }
            context.previousReport?.let { add("- report:${it.id} : 직전 완료 리포트") }
            evidence.forEach { add("- ${it.id} : ${it.title}") }
        }.joinToString("\n")
        return ReportFeedbackPrompt(
            system = """
                당신은 약용작물 재배 회고 코치다. 제공된 근거에 없는 수치나 사실을 만들지 않는다.
                지정된 대상 작업 타입 하나만 회고하고 다른 작업을 비교하거나 권고하지 않는다.
                summary, strengths, improvements, nextActions를 구조화해 응답한다.
                strengths, improvements, nextActions는 근거가 없으면 빈 배열로 응답해도 된다.
                각 항목은 basis, text, evidenceRefs를 가져야 한다.
                evidenceRefs에는 허용 evidenceRefs에 나열된 값을 정확히 그대로 사용한다.
                통계 필드명이나 통계값은 evidenceRefs로 사용하지 않는다.
                기술 문서가 없으면 기술 권고를 억지로 만들지 말고 기록 근거의 코칭만 제공한다.
                같은 항목을 반복하지 말고, 다음 사이클 계획은 실행 방법이 드러나게 작성한다.
                summary와 모든 text는 친근한 존댓말로 끝낸다.
                다음 행동은 "~하세요."처럼, 회고와 요약은 "~했어요."처럼 작성한다.
            """.trimIndent(),
            user = buildString {
                appendLine("허용 evidenceRefs:")
                appendLine(allowedEvidenceRefs)
                appendLine()
                appendLine("대상 리포트: ${context.report.farmName} / ${context.report.cropName}")
                appendLine("작업 타입: ${context.workType.name} (${context.workType.label})")
                appendLine("기간: ${context.report.startsAt}~${context.report.endsAt}")
                appendLine("통계: ${context.report.statistics}")
                appendLine(previous)
                appendLine("대상 기록:")
                appendLine(records)
                appendLine("공식 기술 문서:")
                append(documents.ifBlank { "없음" })
            },
        )
    }
}
