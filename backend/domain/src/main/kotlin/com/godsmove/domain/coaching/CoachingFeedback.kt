package com.godsmove.domain.coaching

import com.godsmove.domain.common.BaseTimeEntity
import com.godsmove.domain.crop.Crop
import com.godsmove.domain.farm.Farm
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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
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
    @Column(name = "coaching_mode", nullable = false, length = 32)
    val coachingMode: CoachingMode,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    val record: FarmingRecord? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id")
    val farm: Farm? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    val crop: Crop? = null,

    @Column(nullable = false, columnDefinition = "text")
    val question: String,

    @Column(name = "period_starts_on")
    val periodStartsOn: LocalDate? = null,

    @Column(name = "period_ends_on")
    val periodEndsOn: LocalDate? = null,

    @Column(nullable = false, columnDefinition = "text")
    val summary: String,

    @Column(name = "risk_level", nullable = false, length = 32)
    val riskLevel: String,

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    val confidenceScore: BigDecimal,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_result", nullable = false, columnDefinition = "jsonb")
    val structuredResult: Map<String, Any?>,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val citations: List<Map<String, Any?>>,

    @Column(name = "audit_status", nullable = false, length = 32)
    val auditStatus: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_warnings", nullable = false, columnDefinition = "jsonb")
    val auditWarnings: List<String>,

    @Column(name = "model_name", nullable = false, length = 128)
    val modelName: String,

    @Column(name = "embedding_model", nullable = false, length = 128)
    val embeddingModel: String,
) : BaseTimeEntity()
