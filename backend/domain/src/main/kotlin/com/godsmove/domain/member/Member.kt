package com.godsmove.domain.member

import com.godsmove.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(length = 32)
    val phone: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val status: MemberStatus = MemberStatus.ACTIVE,

    @Column(length = 64)
    val name: String? = null,

    @Column(length = 64)
    val nickname: String? = null,

    @Column(length = 128)
    val region: String? = null,

    @Column(name = "experience_level", length = 32)
    val experienceLevel: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "management_type", nullable = false, length = 32)
    val managementType: ManagementType = ManagementType.UNREGISTERED,

    @Column(nullable = true)
    val passwordHash: String?,

    @Column(nullable = false)
    val role: String = "ROLE_USER",

    @Column(name = "withdrawn_at")
    val withdrawnAt: LocalDateTime? = null,
) : BaseTimeEntity()
