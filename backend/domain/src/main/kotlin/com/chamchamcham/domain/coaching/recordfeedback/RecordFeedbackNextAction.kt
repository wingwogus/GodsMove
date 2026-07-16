package com.chamchamcham.domain.coaching.recordfeedback

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
import jakarta.persistence.UniqueConstraint
import java.util.UUID

data class RecordFeedbackNextActionDraft(
    val due: RecordFeedbackActionDue,
    val category: RecordFeedbackActionCategory,
    val basis: String,
    val text: String,
)

@Entity
@Table(
    name = "record_feedback_next_action",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_record_feedback_next_action_order",
            columnNames = ["record_feedback_id", "display_order"],
        ),
    ],
)
class RecordFeedbackNextAction(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_feedback_id", nullable = false)
    val recordFeedback: RecordFeedback,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val due: RecordFeedbackActionDue,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val category: RecordFeedbackActionCategory,

    @Column(nullable = false, length = 255)
    val basis: String,

    @Column(nullable = false, length = 255)
    val text: String,
)
