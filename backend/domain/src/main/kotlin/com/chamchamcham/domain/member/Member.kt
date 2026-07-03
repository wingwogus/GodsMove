package com.chamchamcham.domain.member

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = true, unique = true)
    val email: String?,

    @Column(length = 32)
    var phone: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val status: MemberStatus = MemberStatus.ACTIVE,

    @Column(length = 64)
    var name: String? = null,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(length = 64)
    var nickname: String? = null,

    @Column(name = "experience_level")
    var experienceLevel: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "management_type", length = 64)
    var managementType: ManagementType? = null,

    @Column(nullable = true)
    val passwordHash: String?,

    @Column(nullable = false)
    val role: String = "ROLE_USER",

    @Column(name = "withdrawn_at")
    val withdrawnAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    fun completeOnboarding(
        name: String,
        phone: String,
        birthDate: LocalDate,
        nickname: String,
        experienceLevel: Int,
        managementType: ManagementType
    ) {
        this.name = name
        this.phone = phone
        this.birthDate = birthDate
        this.nickname = nickname
        this.experienceLevel = experienceLevel
        this.managementType = managementType
    }

    fun prefillProfile(
        name: String?,
        phone: String?,
        birthDate: LocalDate?
    ) {
        if (this.name.isNullOrBlank() && !name.isNullOrBlank()) {
            this.name = name
        }
        if (this.phone.isNullOrBlank() && !phone.isNullOrBlank()) {
            this.phone = phone
        }
        if (this.birthDate == null && birthDate != null) {
            this.birthDate = birthDate
        }
    }
}
