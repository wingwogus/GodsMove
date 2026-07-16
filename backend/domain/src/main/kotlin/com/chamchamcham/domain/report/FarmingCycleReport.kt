package com.chamchamcham.domain.report

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

data class FarmingCycleReportProjection(
    val status: FarmingCycleReportStatus,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val startBasis: FarmingCycleStartBasis,
    val finalHarvestRecord: FarmingRecord?,
    val statisticsSchemaVersion: Int,
    val statistics: CycleReportStatistics,
)

@Entity
@Table(name = "farming_cycle_report")
class FarmingCycleReport private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val farm: Farm,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    val crop: Crop,

    status: FarmingCycleReportStatus,
    startsAt: LocalDateTime,
    endsAt: LocalDateTime?,
    startBasis: FarmingCycleStartBasis,
    finalHarvestRecord: FarmingRecord?,
    statisticsSchemaVersion: Int,
    statistics: CycleReportStatistics,
    sourceRevision: Long,
) : BaseTimeEntity() {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: FarmingCycleReportStatus = status
        private set

    @Column(name = "starts_at", nullable = false)
    var startsAt: LocalDateTime = startsAt
        private set

    @Column(name = "ends_at")
    var endsAt: LocalDateTime? = endsAt
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "start_basis", nullable = false, length = 48)
    var startBasis: FarmingCycleStartBasis = startBasis
        private set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_harvest_record_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var finalHarvestRecord: FarmingRecord? = finalHarvestRecord
        private set

    @Column(name = "statistics_schema_version", nullable = false)
    var statisticsSchemaVersion: Int = statisticsSchemaVersion
        private set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var statistics: CycleReportStatistics = statistics
        private set

    @Column(name = "source_revision", nullable = false)
    var sourceRevision: Long = sourceRevision
        private set

    fun applyProjection(next: FarmingCycleReportProjection): Boolean {
        next.requireValid()

        if (isSameProjection(next)) {
            return false
        }

        status = next.status
        startsAt = next.startsAt
        endsAt = next.endsAt
        startBasis = next.startBasis
        finalHarvestRecord = next.finalHarvestRecord
        statisticsSchemaVersion = next.statisticsSchemaVersion
        statistics = next.statistics
        sourceRevision += 1
        return true
    }

    fun supersede(): Boolean {
        if (status == FarmingCycleReportStatus.SUPERSEDED) {
            return false
        }

        status = FarmingCycleReportStatus.SUPERSEDED
        sourceRevision += 1
        return true
    }

    private fun isSameProjection(next: FarmingCycleReportProjection): Boolean {
        return status == next.status &&
            startsAt == next.startsAt &&
            endsAt == next.endsAt &&
            startBasis == next.startBasis &&
            finalHarvestRecord?.id == next.finalHarvestRecord?.id &&
            statisticsSchemaVersion == next.statisticsSchemaVersion &&
            statistics == next.statistics
    }

    companion object {
        fun create(
            member: Member,
            farm: Farm,
            crop: Crop,
            projection: FarmingCycleReportProjection,
        ): FarmingCycleReport {
            projection.requireValid()
            return FarmingCycleReport(
                member = member,
                farm = farm,
                crop = crop,
                status = projection.status,
                startsAt = projection.startsAt,
                endsAt = projection.endsAt,
                startBasis = projection.startBasis,
                finalHarvestRecord = projection.finalHarvestRecord,
                statisticsSchemaVersion = projection.statisticsSchemaVersion,
                statistics = projection.statistics,
                sourceRevision = 1,
            )
        }
    }
}

private fun FarmingCycleReportProjection.requireValid() {
    require(statisticsSchemaVersion > 0)
    require(endsAt == null || !startsAt.isAfter(endsAt))
    when (status) {
        FarmingCycleReportStatus.ACTIVE -> {
            require(endsAt == null)
            require(finalHarvestRecord == null)
        }
        FarmingCycleReportStatus.COMPLETED -> {
            require(endsAt != null)
            require(finalHarvestRecord != null)
        }
        FarmingCycleReportStatus.SUPERSEDED ->
            throw IllegalArgumentException("SUPERSEDED is not a source projection")
    }
}
