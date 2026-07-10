package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FarmingRecordRepository : JpaRepository<FarmingRecord, UUID> {
    fun findByIdAndMember_Id(id: UUID, memberId: UUID): FarmingRecord?
    fun findByIdAndIsDeletedFalse(id: UUID): FarmingRecord?

    @Query(
        """
        select r from FarmingRecord r
        join fetch r.member
        join fetch r.farm
        join fetch r.crop
        where r.id = :id
          and r.member.id = :memberId
          and r.isDeleted = false
        """
    )
    fun findContextSourceByIdAndMemberId(id: UUID, memberId: UUID): FarmingRecord?

    @Query(
        """
        select r from FarmingRecord r
        join fetch r.member
        join fetch r.farm
        join fetch r.crop
        where r.member.id = :memberId
          and r.farm.id = :farmId
          and r.crop.id = :cropId
          and r.isDeleted = false
        order by r.workedAt asc, r.id asc
        """
    )
    fun findReportSourceRecords(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): List<FarmingRecord>
}
