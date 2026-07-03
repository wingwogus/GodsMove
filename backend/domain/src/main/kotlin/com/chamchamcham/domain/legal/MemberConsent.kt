package com.chamchamcham.domain.legal

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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "member_consent",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_member_consent_member_document",
            columnNames = ["member_id", "legal_document_id"]
        )
    ]
)
class MemberConsent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_document_id", nullable = false)
    val legalDocument: LegalDocument,

    @Column(nullable = false)
    val agreed: Boolean,

    @Column(name = "agreed_at", nullable = false)
    val agreedAt: LocalDateTime,
) : BaseTimeEntity()
