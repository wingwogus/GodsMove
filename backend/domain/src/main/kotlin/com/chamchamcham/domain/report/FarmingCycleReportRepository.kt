package com.chamchamcham.domain.report

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.UUID

interface FarmingCycleReportRepository : JpaRepository<FarmingCycleReport, UUID> {
    fun findByIdAndMember_Id(id: UUID, memberId: UUID): FarmingCycleReport?

    fun findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        status: FarmingCycleReportStatus,
    ): FarmingCycleReport?

    fun findTopByMember_IdAndFarm_IdAndCrop_IdAndStatusAndEndsAtBeforeAndIdNotOrderByEndsAtDescIdDesc(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        status: FarmingCycleReportStatus,
        beforeEndsAt: LocalDateTime,
        excludedReportId: UUID,
    ): FarmingCycleReport?

    @Query(
        """
        select r from FarmingCycleReport r
        where r.member.id = :memberId
          and r.farm.id = :farmId
          and r.crop.id = :cropId
          and r.status in (
            com.chamchamcham.domain.report.FarmingCycleReportStatus.ACTIVE,
            com.chamchamcham.domain.report.FarmingCycleReportStatus.COMPLETED
          )
        order by r.startsAt asc, r.id asc
        """,
    )
    fun findAllCurrent(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): List<FarmingCycleReport>
}
