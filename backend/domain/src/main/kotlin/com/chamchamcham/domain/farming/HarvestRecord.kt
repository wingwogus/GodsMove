package com.chamchamcham.domain.farming

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.CropUsePartCategory
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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "harvest_record")
class   HarvestRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val record: FarmingRecord,

    @Column(name = "harvest_amount", precision = 18, scale = 4)
    val harvestAmount: BigDecimal?,

    @Enumerated(EnumType.STRING)
    @Column(name = "medicinal_part", length = 32)
    val medicinalPart: CropUsePartCategory? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "harvest_source", nullable = false, length = 16)
    val harvestSource: HarvestSource = HarvestSource.CULTIVATED,

    @Column(name = "growth_period", nullable = false)
    val growthPeriod: Int,

    @Column(name = "is_last_harvest", nullable = false)
    val isLastHarvest: Boolean,
) : BaseTimeEntity()
