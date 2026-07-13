package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import java.time.LocalDateTime
import java.util.UUID

const val REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION = 2

data class ReportFeedbackContext(
    val schemaVersion: Int,
    val workType: WorkType,
    val report: ReportFeedbackReport,
    val records: List<ReportFeedbackRecord>,
    val previousReport: ReportFeedbackPreviousReport?,
    val warnings: List<String>,
)

data class ReportFeedbackReport(
    val id: UUID,
    val farmName: String,
    val cropName: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val statistics: Map<String, Any?>,
)

data class ReportFeedbackRecord(
    val id: UUID,
    val workedAt: LocalDateTime,
    val workType: WorkType,
    val memo: String,
    val details: Map<String, Any?>,
)

data class ReportFeedbackPreviousReport(
    val id: UUID,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val statistics: Map<String, Any?>,
)
