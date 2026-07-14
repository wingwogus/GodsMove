package com.chamchamcham.domain.coaching.reportfeedback

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.FarmingCycleReport
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(
    name = "report_feedback",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_report_feedback_report_work_type",
            columnNames = ["report_id", "work_type"],
        ),
    ],
)
class ReportFeedback(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    val report: FarmingCycleReport,

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 32)
    val workType: WorkType,

    status: ReportFeedbackStatus,

    inputSnapshot: Map<String, Any?>? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var citations: List<Map<String, Any?>> = emptyList(),

    @Column(name = "audit_status", length = 32)
    var auditStatus: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_warnings", nullable = false, columnDefinition = "jsonb")
    var auditWarnings: List<String> = emptyList(),

    failureCode: String? = null,

    @Column(name = "model_name", length = 128)
    var modelName: String? = null,

    @Column(name = "embedding_model", length = 128)
    var embeddingModel: String? = null,
) : BaseTimeEntity() {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: ReportFeedbackStatus = status
        private set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    var inputSnapshot: Map<String, Any?>? = inputSnapshot
        private set

    @Column(name = "failure_code", length = 128)
    var failureCode: String? = failureCode
        private set

    @Column(columnDefinition = "text")
    var summary: String? = null
        private set

    @OneToMany(mappedBy = "reportFeedback", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private val itemRows = mutableListOf<ReportFeedbackItem>()

    fun items(): List<ReportFeedbackItem> = itemRows.toList()

    fun attachInputSnapshot(snapshot: Map<String, Any?>) {
        check(status == ReportFeedbackStatus.PENDING) { "input snapshot can only be attached while pending" }
        inputSnapshot = snapshot
    }

    fun markFailed(code: String) {
        check(status == ReportFeedbackStatus.PENDING) { "only pending feedback can fail" }
        status = ReportFeedbackStatus.FAILED
        failureCode = code
    }

    fun retry() {
        check(status == ReportFeedbackStatus.FAILED) { "only failed feedback can retry" }
        status = ReportFeedbackStatus.PENDING
        failureCode = null
        inputSnapshot = null
    }

    fun markReady(
        summary: String,
        items: List<ReportFeedbackItemDraft>,
        citations: List<Map<String, Any?>>,
        auditStatus: String,
        auditWarnings: List<String>,
        modelName: String,
        embeddingModel: String,
    ) {
        check(status == ReportFeedbackStatus.PENDING) { "only pending feedback can become ready" }
        require(summary.isNotBlank()) { "summary is required" }
        require(items.all { it.basis.isNotBlank() && it.text.isNotBlank() }) {
            "feedback item basis and text are required"
        }

        this.summary = summary
        itemRows.clear()
        items.forEachIndexed { index, item ->
            itemRows += ReportFeedbackItem(
                reportFeedback = this,
                displayOrder = index,
                section = item.section,
                basis = item.basis,
                text = item.text,
            )
        }
        this.citations = citations
        this.auditStatus = auditStatus
        this.auditWarnings = auditWarnings
        this.modelName = modelName
        this.embeddingModel = embeddingModel
        status = ReportFeedbackStatus.READY
        failureCode = null
    }

    companion object {
        fun pending(member: Member, report: FarmingCycleReport, workType: WorkType): ReportFeedback =
            ReportFeedback(
                member = member,
                report = report,
                workType = workType,
                status = ReportFeedbackStatus.PENDING,
            )

    }
}
