package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "policy_recommendation")
class PolicyRecommendation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_program_id", nullable = false)
    val policyProgram: PolicyProgram,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_sync_job_id", nullable = false)
    val sourceSyncJob: PolicySyncJob,

    @Column(nullable = false, precision = 8, scale = 4)
    val score: BigDecimal,

    @Column(nullable = false, length = 1000)
    val reason: String,
) : BaseTimeEntity()
