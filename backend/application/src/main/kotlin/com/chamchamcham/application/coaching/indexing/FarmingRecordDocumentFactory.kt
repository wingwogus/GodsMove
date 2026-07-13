package com.chamchamcham.application.coaching.indexing

import com.chamchamcham.application.coaching.common.RagSourceType
import org.springframework.ai.document.Document
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class IndexedFarmingRecord(
    val recordId: UUID,
    val memberId: UUID,
    val farmId: UUID,
    val cropId: UUID,
    val workTypeId: UUID,
    val memberName: String?,
    val farmAddress: String?,
    val farmName: String,
    val cropName: String,
    val workTypeName: String,
    val workedAt: LocalDateTime,
    val memo: String?,
    val fieldValues: List<String>
)

@Component
class FarmingRecordDocumentFactory {
    fun build(record: IndexedFarmingRecord): Document {
        val workedOn = record.workedAt.toLocalDate()
        val content = buildString {
            appendLine("농업인: ${record.memberName ?: "미입력"}")
            appendLine("농장 주소: ${record.farmAddress ?: "미입력"}")
            appendLine("농장: ${record.farmName}")
            appendLine("작물: ${record.cropName}")
            appendLine("작업일시: ${record.workedAt}")
            appendLine("작업유형: ${record.workTypeName}")
            appendLine("영농일지: ${record.memo ?: "기록 없음"}")
            if (record.fieldValues.isNotEmpty()) {
                appendLine("세부 항목:")
                record.fieldValues.forEach { appendLine("- $it") }
            }
        }.trim()

        return Document(
            record.recordId.toString(),
            content,
            mapOf(
                "label" to "${record.workTypeName} $workedOn",
                "sourceType" to RagSourceType.FARMING_RECORD.name,
                "sourceId" to record.recordId.toString(),
                "memberId" to record.memberId.toString(),
                "farmId" to record.farmId.toString(),
                "cropId" to record.cropId.toString(),
                "workTypeId" to record.workTypeId.toString(),
                "recordId" to record.recordId.toString(),
                "workedAt" to workedOn.toString(),
                "workedAtEpochDay" to workedOn.toEpochDay(),
                "workedAtEpochSecond" to record.workedAt.toEpochSecond(ZoneOffset.UTC)
            )
        )
    }
}
