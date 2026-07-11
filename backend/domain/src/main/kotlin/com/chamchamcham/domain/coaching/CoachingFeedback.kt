package com.chamchamcham.domain.coaching

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.FarmingCycleReport
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
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "coaching_feedback")
class CoachingFeedback(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 32)
    val feedbackType: FeedbackType,

    status: CoachingFeedbackStatus,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    val record: FarmingRecord? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_report_id")
    val cycleReport: FarmingCycleReport? = null,

    @Column(name = "source_revision", nullable = false)
    val sourceRevision: Long,

    inputSnapshot: Map<String, Any?>? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_result", columnDefinition = "jsonb")
    var structuredResult: Map<String, Any?>? = null,

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
    var status: CoachingFeedbackStatus = status
        private set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    var inputSnapshot: Map<String, Any?>? = inputSnapshot
        private set

    @Column(name = "failure_code", length = 128)
    var failureCode: String? = failureCode
        private set

    init {
        require(sourceRevision > 0) { "sourceRevision must be positive" }
        require((record == null) != (cycleReport == null)) { "exactly one feedback target is required" }
        require(feedbackType != FeedbackType.RECORD || record != null) { "record feedback requires a record target" }
        require(feedbackType != FeedbackType.CYCLE_REPORT || cycleReport != null) {
            "cycle report feedback requires a cycle report target"
        }
    }

    fun attachInputSnapshot(snapshot: Map<String, Any?>) {
        check(status == CoachingFeedbackStatus.PENDING) { "input snapshot can only be attached while pending" }
        inputSnapshot = snapshot
    }

    fun markFailed(code: String) {
        check(status == CoachingFeedbackStatus.PENDING) { "only pending feedback can fail" }
        status = CoachingFeedbackStatus.FAILED
        failureCode = code
    }

    fun markReady(
        structuredResult: Map<String, Any?>,
        citations: List<Map<String, Any?>>,
        auditStatus: String,
        auditWarnings: List<String>,
        modelName: String,
        embeddingModel: String,
    ) {
        check(status == CoachingFeedbackStatus.PENDING) { "only pending feedback can become ready" }
        this.structuredResult = structuredResult
        this.citations = citations
        this.auditStatus = auditStatus
        this.auditWarnings = auditWarnings
        this.modelName = modelName
        this.embeddingModel = embeddingModel
        status = CoachingFeedbackStatus.READY
        failureCode = null
    }

    fun retry() {
        check(status == CoachingFeedbackStatus.FAILED) { "only failed feedback can retry" }
        status = CoachingFeedbackStatus.PENDING
        failureCode = null
        inputSnapshot = null
    }

    fun markStale() {
        if (status == CoachingFeedbackStatus.STALE) {
            return
        }
        check(status == CoachingFeedbackStatus.PENDING || status == CoachingFeedbackStatus.READY) {
            "only pending or ready feedback can become stale"
        }
        status = CoachingFeedbackStatus.STALE
    }

    companion object {
        fun pendingRecord(
            member: Member,
            record: FarmingRecord,
            sourceRevision: Long,
        ): CoachingFeedback {
            return CoachingFeedback(
                member = member,
                feedbackType = FeedbackType.RECORD,
                status = CoachingFeedbackStatus.PENDING,
                record = record,
                sourceRevision = sourceRevision,
            )
        }
    }
}
