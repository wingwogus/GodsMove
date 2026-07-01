package com.godsmove.domain.crop

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "crop")
class Crop(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = false, unique = true, length = 128)
    val name: String,

    @Column(nullable = false, length = 64)
    val category: String,

    @Column(name = "lifecycle_type", nullable = false, length = 32)
    val lifecycleType: String,

    @Column(name = "default_unit", nullable = false, length = 32)
    val defaultUnit: String,
)
