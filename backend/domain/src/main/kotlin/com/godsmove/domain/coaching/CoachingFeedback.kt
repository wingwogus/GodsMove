package com.godsmove.domain.coaching

import com.godsmove.domain.crop.Crop
import com.godsmove.domain.farming.FarmingRecord
import com.godsmove.domain.member.Member
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
import java.time.LocalDate
import java.time.LocalDateTime
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    val record: FarmingRecord? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 32)
    val feedbackType: FeedbackType,

    @Column(name = "period_starts_on")
    val periodStartsOn: LocalDate? = null,

    @Column(name = "period_ends_on")
    val periodEndsOn: LocalDate? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    val crop: Crop? = null,

    @Column(nullable = false, columnDefinition = "text")
    val summary: String,

    @Column(name = "next_actions", columnDefinition = "text")
    val nextActions: String? = null,

    @Column(name = "input_summary", columnDefinition = "text")
    val inputSummary: String? = null,

    @Column(name = "source_refs", columnDefinition = "text")
    val sourceRefs: String? = null,

    @Column(name = "model_name", nullable = false, length = 128)
    val modelName: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
