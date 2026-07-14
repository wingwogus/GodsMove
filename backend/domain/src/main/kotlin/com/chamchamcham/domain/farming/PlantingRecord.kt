package com.chamchamcham.domain.farming

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "planting_record")
class PlantingRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    val record: FarmingRecord,

    @Enumerated(EnumType.STRING)
    @Column(name = "planting_method", nullable = false, length = 32)
    val plantingMethod: PlantingMethod,

    @Column(name = "seed_amount", precision = 18, scale = 4)
    val seedAmount: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "seed_amount_unit", length = 16)
    val seedAmountUnit: SeedAmountUnit? = null,

    @Column(name = "seedling_count")
    val seedlingCount: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "seedling_unit", length = 16)
    val seedlingUnit: SeedlingUnit? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "propagation_method", length = 32)
    val propagationMethod: PropagationMethod? = null,
) : BaseTimeEntity()
