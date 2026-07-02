package com.chamchamcham.domain.legal

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "legal_document",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_legal_document_type_version",
            columnNames = ["document_type", "version"]
        )
    ]
)
class LegalDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "document_type", nullable = false, length = 64)
    val documentType: String,

    @Column(nullable = false, length = 255)
    val title: String,

    @Column(nullable = false, length = 32)
    val version: String,

    @Column(nullable = false, columnDefinition = "text")
    val body: String,

    @Column(name = "published_at", nullable = false)
    val publishedAt: LocalDateTime,
) : BaseTimeEntity()
