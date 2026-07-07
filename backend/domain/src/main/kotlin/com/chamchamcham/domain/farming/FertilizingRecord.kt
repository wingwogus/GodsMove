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
@Table(name = "fertilizing_record")
class FertilizingRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    val record: FarmingRecord,

    @Column(name = "material_name", nullable = false, length = 255)
    val materialName: String,

    @Column(nullable = false, precision = 18, scale = 4)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_unit", nullable = false, length = 16)
    val amountUnit: FertilizerAmountUnit,

    @Enumerated(EnumType.STRING)
    @Column(name = "application_method", length = 16)
    val applicationMethod: FertilizingMethod? = null,
) : BaseTimeEntity()
