package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.ManagementType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "policy_program")
class PolicyProgram(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = false, length = 255)
    val title: String,

    @Column(nullable = false, columnDefinition = "text")
    val body: String,

    @Column(nullable = false, length = 128)
    val region: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_management_type", nullable = false, length = 32)
    val targetManagementType: ManagementType,

    @Column(name = "apply_starts_on")
    val applyStartsOn: LocalDate? = null,

    @Column(name = "apply_ends_on")
    val applyEndsOn: LocalDate? = null,

    @Column(name = "source_url", length = 2048)
    val sourceUrl: String? = null,
) : BaseTimeEntity()
