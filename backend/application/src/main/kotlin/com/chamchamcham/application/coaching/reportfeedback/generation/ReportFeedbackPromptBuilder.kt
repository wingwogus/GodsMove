package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.CoachingTextPolicy
import com.chamchamcham.application.coaching.common.toCoachingText
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Component

@Component
class ReportFeedbackPromptBuilder {
    fun build(
        context: ReportFeedbackContext,
        evidence: List<ReportFeedbackEvidence>,
    ): ReportFeedbackPrompt {
        val records = context.records.joinToString("\n", transform = ::formatRecord)
        val documents = evidence.joinToString("\n") { "- ${it.id}: ${it.title} / ${it.content}" }
        val allowedEvidenceRefs = buildList {
            add("- report:${context.report.id} : 현재 완료 리포트")
            context.records.forEach { add("- record:${it.id} : 대상 영농기록") }
            context.previousReport?.let { add("- report:${it.id} : 직전 완료 리포트") }
            evidence.forEach { add("- ${it.id} : ${it.title}") }
        }.joinToString("\n")

        return ReportFeedbackPrompt(
            system = systemPrompt(),
            user = buildString {
                appendLine("허용 evidenceRefs:")
                appendLine(allowedEvidenceRefs.ifBlank { "없음" })
                appendLine()
                appendLine("대상 리포트: ${context.report.farmName} / ${context.report.cropName}")
                appendLine("대상 작업: ${context.workType.toCoachingText()}")
                appendLine("기간: ${context.report.startsAt}~${context.report.endsAt}")
                appendLine("현재 작업 통계:")
                formatStatistics(context.workType, context.report.statistics)
                    .forEach { appendLine("- $it") }
                appendLine(formatPreviousReport(context))
                appendLine("서버가 계산한 직전 동일 작업 비교:")
                if (context.comparisons.isEmpty()) {
                    appendLine("없음")
                } else {
                    context.comparisons.forEach { appendLine("- ${formatComparison(it)}") }
                }
                appendLine("대상 기록:")
                appendLine(records)
                appendLine("공식 기술 문서:")
                append(documents.ifBlank { "없음" })
            },
        )
    }

    private fun systemPrompt(): String {
        return """
            당신은 약용작물 재배 회고 코치다. 제공된 근거에 없는 수치나 사실을 만들지 않는다.
            지정된 대상 작업 타입 하나만 회고하고 다른 작업을 비교하거나 권고하지 않는다.
            직전 리포트 비교는 제공된 동일 작업 통계만 사용하고 이전 기록이나 메모를 본 것처럼 말하지 않는다.
            summary, comparisons, strengths, improvements, nextActions를 구조화해 응답한다.
            strengths, improvements, nextActions는 각각 정확히 1개의 항목으로 응답한다.
            서버가 계산한 비교값이 있으면 comparisons는 정확히 1개의 항목으로 응답한다.
            서버가 계산한 비교값이 없으면 comparisons는 반드시 빈 배열로 응답한다.
            각 배열 항목의 text는 줄바꿈이나 목록 기호 없이 하나의 문단으로 작성한다.
            한 문단 안에는 자연스럽게 이어지는 여러 문장을 작성해도 된다.
            summary는 20~65자로 작성한다.
            comparisons의 text는 20~65자로 작성한다.
            strengths, improvements, nextActions의 text는 각각 20~65자로 작성한다.
            최소 길이를 맞출 때 의미 없는 표현을 덧붙이지 말고 근거, 판단, 실행 방법을 보강해 다시 쓴다.
            65자를 넘으면 문장을 자르지 말고 핵심 내용을 남겨 다시 쓴다.
            각 항목은 basis, text, evidenceRefs를 가져야 한다.
            evidenceRefs에는 허용 evidenceRefs에 나열된 값을 정확히 그대로 사용한다.
            통계 필드명이나 통계값은 evidenceRefs로 사용하지 않는다.
            기술 문서가 없더라도 현재 리포트와 대상 기록을 근거로 strengths, improvements, nextActions를 각각 작성한다.
            공식문서가 필요한 기술적 주장은 문서 근거 없이 만들지 않는다.
            서버가 계산한 비교값을 그대로 사용하고 다시 계산하지 않는다.
            comparisons에는 변화 사실만 쉬운 문장으로 쓰고 칭찬, 문제 진단, 권고를 넣지 않는다.
            comparison, strength, improvement, next-action 사이에 같은 내용을 반복하지 않는다.
            같은 항목을 반복하지 말고, 선택한 작업의 다음 행동은 실행 방법이 드러나게 작성한다.
            summary와 모든 text는 친근한 존댓말로 끝낸다.
            다음 행동은 "~하세요."처럼, 회고와 요약은 "~했어요."처럼 작성한다.
        """.trimIndent() + "\n" + CoachingTextPolicy.promptInstructions
    }

    private fun formatPreviousReport(context: ReportFeedbackContext): String {
        val previous = context.previousReport ?: return "직전 완료 리포트 없음"
        return buildString {
            appendLine(
                "직전 완료 리포트(report:${previous.id}): " +
                    "${previous.startsAt}~${previous.endsAt}",
            )
            formatStatistics(context.workType, previous.statistics, prefix = "직전 ")
                .forEach { appendLine("- $it") }
        }.trim()
    }

    private fun formatComparison(comparison: ReportFeedbackComparison): String {
        val unit = comparison.unit.unitText() ?: comparison.unit
        val difference = comparison.difference
        val direction = when {
            difference.signum() > 0 -> "${difference.abs().toPlainString()}$unit 늘었고"
            difference.signum() < 0 -> "${difference.abs().toPlainString()}$unit 줄었고"
            else -> "변화가 없고"
        }
        val relative = comparison.relativeChangePct?.let {
            " 변화율은 ${it.abs().toPlainString()}퍼센트예요."
        } ?: " 변화율은 계산하지 않았어요."
        val coverage = formatCoverage(comparison)
        return "직전보다 ${comparison.metricLabel}${comparison.metricLabel.subjectParticle()} $direction$relative$coverage"
    }

    private fun formatCoverage(comparison: ReportFeedbackComparison): String {
        val current = comparison.currentCoverage ?: return ""
        val previous = comparison.previousCoverage ?: return ""
        return " 입력 범위는 이번 ${current.recordedCount}/${current.targetCount}건, " +
            "직전 ${previous.recordedCount}/${previous.targetCount}건이에요."
    }

    private fun String.subjectParticle(): String {
        val last = lastOrNull() ?: return "가"
        if (last !in '\uAC00'..'\uD7A3') return "가"
        return if ((last.code - '\uAC00'.code) % 28 == 0) "가" else "이"
    }

    private fun formatRecord(record: ReportFeedbackRecord): String {
        return buildString {
            append("- record:${record.id} ${record.workedAt} ${record.workType.toCoachingText()}")
            append(" / 메모: ${record.memo}")
            formatRecordDetails(record.workType, record.details).forEach { detail ->
                append("\n  - $detail")
            }
        }
    }

    private fun formatRecordDetails(
        workType: WorkType,
        details: Map<String, Any?>,
    ): List<String> = buildList {
        details["weatherCondition"]?.toString()?.takeIf(String::isNotBlank)
            ?.let { add("기록 당시 날씨: $it") }
        details.number("weatherTemperature")?.let { add("기록 당시 기온: ${it}도") }
        (details["hasPhoto"] as? Boolean)?.let { add("사진 첨부: ${if (it) "있음" else "없음"}") }

        when (workType) {
            WorkType.PLANTING -> details.nested("planting")?.let { detail ->
                detail.categoryCode("plantingMethod")?.plantingMethodText()
                    ?.let { add("심기: $it") }
                detail.categoryCode("propagationMethod")?.propagationText()
                    ?.let { add("모종 만든 방법: $it") }
                detail.number("quantity")?.let { quantity ->
                    detail["quantityUnit"]?.toString()?.unitText()?.let { unit ->
                        add("심은 양: $quantity$unit")
                    }
                }
            }

            WorkType.WATERING -> details.nested("watering")?.let { detail ->
                detail.categoryCode("amount")?.irrigationAmountText()
                    ?.let { add("물 준 양: $it") }
                detail.categoryCode("method")?.irrigationMethodText()
                    ?.let { add("물을 준 방법: $it") }
            }

            WorkType.FERTILIZING -> details.nested("fertilizing")?.let { detail ->
                detail["materialName"]?.toString()?.takeIf(String::isNotBlank)
                    ?.let { add("거름 이름: $it") }
                detail.number("amount")?.let { amount ->
                    detail["amountUnit"]?.toString()?.unitText()?.let { unit ->
                        add("거름 양: $amount$unit")
                    }
                }
                detail.categoryCode("applicationMethod")?.fertilizingMethodText()
                    ?.let { add("거름을 준 방법: $it") }
            }

            WorkType.PEST_CONTROL -> details.nested("pestControl")?.let { detail ->
                detail["pesticideName"]?.toString()?.takeIf(String::isNotBlank)
                    ?.let { add("약 이름: $it") }
                detail.number("pesticideAmount")?.let { amount ->
                    detail["pesticideAmountUnit"]?.toString()?.unitText()?.let { unit ->
                        add("약 사용량: $amount$unit")
                    }
                }
                detail.number("totalSprayAmountLiters")
                    ?.let { add("약을 섞은 물의 양: ${it}리터") }
                detail["pestName"]?.toString()?.takeIf(String::isNotBlank)
                    ?.let { add("관리 대상: $it") }
            }

            WorkType.WEEDING -> details.nested("weeding")?.let { detail ->
                detail.categoryCode("method")?.weedingMethodText()
                    ?.let { add("풀을 정리한 방법: $it") }
            }

            WorkType.HARVEST -> details.nested("harvest")?.let { detail ->
                detail.number("amountKg")?.let { add("수확량: ${it}킬로그램") }
                detail.categoryCode("medicinalPart")?.cropPartText()
                    ?.let { add("수확한 부위: $it") }
                detail.categoryCode("harvestSource")?.harvestSourceText()
                    ?.let { add("수확한 곳: $it") }
                detail.number("growthPeriod")?.let { period ->
                    detail["growthPeriodUnit"]?.toString()?.growthPeriodUnitText()?.let { unit ->
                        add("기른 기간: ${period.toInt()}$unit")
                    }
                }
                (detail["isLastHarvest"] as? Boolean)
                    ?.let { add("마지막 수확: ${if (it) "네" else "아니요"}") }
            }

            WorkType.PRUNING, WorkType.ETC -> Unit
        }
    }

    private fun formatStatistics(
        workType: WorkType,
        statistics: Map<String, Any?>,
        prefix: String = "",
    ): List<String> = buildList {
        statistics.number("recordCount")?.let { add("${prefix}기록 횟수: ${it.toInt()}회") }
        statistics["firstWorkedOn"]?.toString()?.let { add("${prefix}첫 작업일: $it") }
        statistics["lastWorkedOn"]?.toString()?.let { add("${prefix}마지막 작업일: $it") }
        statistics.number("averageIntervalDays")?.let { add("${prefix}평균 작업 간격: ${it}일") }
        statistics.number("photoAttachedRecordCount")
            ?.let { add("${prefix}사진을 붙인 기록: ${it.toInt()}회") }
        statistics.number("averageTemperatureC")?.let { add("${prefix}평균 기온: ${it}도") }

        when (workType) {
            WorkType.PLANTING -> addAll(
                statistics.distribution(
                    "propagationMethods",
                    "code",
                    prefix,
                    { it.plantingMethodText() ?: it.propagationText() },
                ),
            )

            WorkType.WATERING -> {
                addAll(
                    statistics.distribution(
                        "amountDistribution",
                        "code",
                        prefix,
                        { it.irrigationAmountText() },
                        label = "물 준 양",
                    ),
                )
                addAll(
                    statistics.distribution(
                        "methodDistribution",
                        "code",
                        prefix,
                        { it.irrigationMethodText() },
                        label = "물을 준 방법",
                    ),
                )
            }

            WorkType.FERTILIZING -> {
                statistics.number("totalAmountKg")?.let { add("${prefix}거름 총량: ${it}킬로그램") }
                statistics.number("averageAmountKg")?.let { add("${prefix}한 번 평균 거름 양: ${it}킬로그램") }
                addAll(
                    statistics.distribution(
                        "materialDistribution",
                        "label",
                        prefix,
                        { it.takeIf(String::isNotBlank) },
                        label = "거름 이름",
                    ),
                )
                addAll(
                    statistics.distribution(
                        "methodDistribution",
                        "code",
                        prefix,
                        { it.fertilizingMethodText() },
                        label = "거름을 준 방법",
                    ),
                )
            }

            WorkType.PEST_CONTROL -> {
                addAll(
                    statistics.distribution(
                        "categoryDistribution",
                        "label",
                        prefix,
                        { it.takeIf(String::isNotBlank) },
                        label = "약 이름",
                    ),
                )
                statistics.listOfMaps("pesticideAmounts").forEach { item ->
                    val amount = item.number("amount") ?: return@forEach
                    val unit = item["unit"]?.toString()?.unitText() ?: return@forEach
                    add("${prefix}약 사용량: $amount$unit")
                }
                statistics.number("totalSprayAmountLiters")
                    ?.let { add("${prefix}약을 섞은 물의 총량: ${it}리터") }
                statistics.listOfMaps("targets").forEach { item ->
                    val target = item["target"]?.toString()?.takeIf(String::isNotBlank) ?: return@forEach
                    val count = item.number("count")?.toInt() ?: return@forEach
                    add("${prefix}관리 대상: $target ${count}회")
                }
            }

            WorkType.WEEDING -> addAll(
                statistics.distribution(
                    "methodDistribution",
                    "code",
                    prefix,
                    { it.weedingMethodText() },
                    label = "풀을 정리한 방법",
                ),
            )

            WorkType.HARVEST -> {
                statistics.number("totalAmountKg")?.let { add("${prefix}총수확량: ${it}킬로그램") }
                statistics.number("averageAmountKg")?.let { add("${prefix}한 번 평균 수확량: ${it}킬로그램") }
                addAll(
                    statistics.distribution(
                        "medicinalParts",
                        "code",
                        prefix,
                        { it.cropPartText() },
                        label = "수확한 부위",
                    ),
                )
                statistics.number("finalGrowthPeriodMonths")
                    ?.let { add("${prefix}마지막 수확까지 기른 기간: ${it.toInt()}개월") }
            }

            WorkType.PRUNING, WorkType.ETC -> Unit
        }
    }

    private fun Map<String, Any?>.distribution(
        listKey: String,
        codeKey: String,
        prefix: String,
        labeler: (String) -> String?,
        label: String = "심은 방법",
    ): List<String> = listOfMaps(listKey).mapNotNull { item ->
        val value = item[codeKey]?.toString()?.let(labeler) ?: return@mapNotNull null
        val count = item.number("count")?.toInt()
            ?: item.number("recordCount")?.toInt()
            ?: return@mapNotNull null
        "$prefix$label: $value ${count}회"
    }

    private fun Map<String, Any?>.number(key: String): Number? = this[key] as? Number

    private fun Map<String, Any?>.nested(key: String): Map<String, Any?>? = this[key].asStringMap()

    private fun Map<String, Any?>.categoryCode(key: String): String? =
        nested(key)?.get("code")?.toString()?.takeIf(String::isNotBlank)

    private fun Map<String, Any?>.listOfMaps(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>)?.mapNotNull { it.asStringMap() }.orEmpty()

    private fun Any?.asStringMap(): Map<String, Any?>? {
        val source = this as? Map<*, *> ?: return null
        return source.entries.mapNotNull { (key, value) ->
            (key as? String)?.let { it to value }
        }.toMap()
    }

    private fun String.propagationText(): String? =
        PropagationMethod.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.plantingMethodText(): String? =
        PlantingMethod.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.irrigationAmountText(): String? =
        IrrigationAmount.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.irrigationMethodText(): String? =
        IrrigationMethod.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.fertilizingMethodText(): String? =
        FertilizingMethod.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.weedingMethodText(): String? =
        WeedingMethod.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.cropPartText(): String? =
        CropUsePartCategory.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.harvestSourceText(): String? =
        HarvestSource.entries.firstOrNull { it.name == this }?.toCoachingText()

    private fun String.growthPeriodUnitText(): String? = when (this) {
        "YEAR" -> "년"
        "MONTH" -> "개월"
        else -> null
    }

    private fun String.unitText(): String? = when (this) {
        "KG" -> "킬로그램"
        "G" -> "그램"
        "ML" -> "밀리리터"
        "L" -> "리터"
        "JU" -> "포기"
        else -> null
    }
}
