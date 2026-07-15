package com.chamchamcham.domain.coaching.reportfeedback

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

data class ReportFeedbackItemDraft(
    val section: ReportFeedbackItemSection,
    val basis: String,
    val text: String,
)

@Entity
@Table(
    name = "report_feedback_item",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_report_feedback_item_order",
            columnNames = ["report_feedback_id", "display_order"],
        ),
    ],
)
class ReportFeedbackItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_feedback_id", nullable = false)
    val reportFeedback: ReportFeedback,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val section: ReportFeedbackItemSection,

    @Column(nullable = false, columnDefinition = "text")
    val basis: String,

    @Column(nullable = false, columnDefinition = "text")
    val text: String,
)
