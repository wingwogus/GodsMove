package com.chamchamcham.domain.farming

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.member.Member
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
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "farming_record")
class FarmingRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    var farm: Farm,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    var crop: Crop,

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 32)
    var workType: WorkType,

    @Column(name = "worked_at", nullable = false)
    var workedAt: LocalDateTime,

    @Column(name = "weather_condition", nullable = false, length = 64)
    var weatherCondition: String,

    @Column(name = "weather_temperature", nullable = false)
    var weatherTemperature: Int,

    @Column(columnDefinition = "text", nullable = false)
    var memo: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_mode", nullable = false, length = 32)
    val entryMode: EntryMode,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,
) : BaseTimeEntity() {
    fun update(
        farm: Farm,
        crop: Crop,
        workType: WorkType,
        workedAt: LocalDateTime,
        weatherCondition: String,
        weatherTemperature: Int,
        memo: String,
    ) {
        this.farm = farm
        this.crop = crop
        this.workType = workType
        this.workedAt = workedAt
        this.weatherCondition = weatherCondition
        this.weatherTemperature = weatherTemperature
        this.memo = memo
    }

    fun softDelete() {
        isDeleted = true
    }
}
