package com.chamchamcham.domain.coaching.recordfeedback

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.member.Member
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
    name = "record_feedback",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_record_feedback_record_revision",
            columnNames = ["record_id", "source_revision"],
        ),
    ],
)
class RecordFeedback(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    val record: FarmingRecord,

    status: RecordFeedbackStatus,

    @Column(name = "source_revision", nullable = false)
    val sourceRevision: Long,

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
    var status: RecordFeedbackStatus = status
        private set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    var inputSnapshot: Map<String, Any?>? = inputSnapshot
        private set

    @Column(name = "failure_code", length = 128)
    var failureCode: String? = failureCode
        private set

    @Column(name = "good_point_basis", length = 255)
    var goodPointBasis: String? = null
        private set

    @Column(name = "good_point_text", length = 255)
    var goodPointText: String? = null
        private set

    @OneToMany(mappedBy = "recordFeedback", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private val nextActionRows = mutableListOf<RecordFeedbackNextAction>()

    init {
        require(sourceRevision > 0) { "sourceRevision must be positive" }
    }

    fun nextActions(): List<RecordFeedbackNextAction> = nextActionRows.toList()

    fun attachInputSnapshot(snapshot: Map<String, Any?>) {
        check(status == RecordFeedbackStatus.PENDING) { "input snapshot can only be attached while pending" }
        inputSnapshot = snapshot
    }

    fun markFailed(code: String) {
        check(status == RecordFeedbackStatus.PENDING) { "only pending feedback can fail" }
        status = RecordFeedbackStatus.FAILED
        failureCode = code
    }

    fun markReady(
        goodPointBasis: String,
        goodPointText: String,
        nextActions: List<RecordFeedbackNextActionDraft>,
        citations: List<Map<String, Any?>>,
        auditStatus: String,
        auditWarnings: List<String>,
        modelName: String,
        embeddingModel: String,
    ) {
        check(status == RecordFeedbackStatus.PENDING) { "only pending feedback can become ready" }
        require(goodPointBasis.isNotBlank()) { "good point basis is required" }
        require(goodPointText.isNotBlank()) { "good point text is required" }
        require(nextActions.size in MIN_NEXT_ACTIONS..MAX_NEXT_ACTIONS) { "next action count must be 2 or 3" }
        require(nextActions.all { it.basis.isNotBlank() && it.text.isNotBlank() }) {
            "next action basis and text are required"
        }

        this.goodPointBasis = goodPointBasis
        this.goodPointText = goodPointText
        nextActionRows.clear()
        nextActions.forEachIndexed { index, action ->
            nextActionRows += RecordFeedbackNextAction(
                recordFeedback = this,
                displayOrder = index,
                due = action.due,
                category = action.category,
                basis = action.basis,
                text = action.text,
            )
        }
        this.citations = citations
        this.auditStatus = auditStatus
        this.auditWarnings = auditWarnings
        this.modelName = modelName
        this.embeddingModel = embeddingModel
        status = RecordFeedbackStatus.READY
        failureCode = null
    }

    fun retry() {
        check(status == RecordFeedbackStatus.FAILED) { "only failed feedback can retry" }
        status = RecordFeedbackStatus.PENDING
        failureCode = null
        inputSnapshot = null
    }

    fun markStale() {
        if (status == RecordFeedbackStatus.STALE) {
            return
        }
        check(status == RecordFeedbackStatus.PENDING || status == RecordFeedbackStatus.READY) {
            "only pending or ready feedback can become stale"
        }
        status = RecordFeedbackStatus.STALE
    }

    companion object {
        private const val MIN_NEXT_ACTIONS = 2
        private const val MAX_NEXT_ACTIONS = 3

        fun pending(
            member: Member,
            record: FarmingRecord,
            sourceRevision: Long,
        ): RecordFeedback {
            return RecordFeedback(
                member = member,
                record = record,
                status = RecordFeedbackStatus.PENDING,
                sourceRevision = sourceRevision,
            )
        }
    }
}
