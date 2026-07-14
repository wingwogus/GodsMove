package com.chamchamcham.domain.farming

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.Pesticide
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "pest_control_record")
class PestControlRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    val record: FarmingRecord,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pesticide_id", nullable = false)
    val pesticide: Pesticide,

    @Column(name = "pesticide_amount", nullable = false, precision = 18, scale = 4)
    val pesticideAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "pesticide_amount_unit", nullable = false, length = 16)
    val pesticideAmountUnit: PesticideAmountUnit,

    @Column(name = "total_spray_amount", nullable = false, precision = 18, scale = 4)
    val totalSprayAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "total_spray_amount_unit", nullable = false, length = 16)
    val totalSprayAmountUnit: SprayAmountUnit,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pest_id")
    val pest: Pest? = null,
) : BaseTimeEntity()
