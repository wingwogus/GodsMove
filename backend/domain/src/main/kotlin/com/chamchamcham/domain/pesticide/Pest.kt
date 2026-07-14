package com.chamchamcham.domain.pesticide

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "pest",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_pest_name", columnNames = ["name"])
    ]
)
class Pest(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = false, length = 128)
    var name: String,
) : BaseTimeEntity()
