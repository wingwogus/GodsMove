package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmingRecordRepository : JpaRepository<FarmingRecord, UUID> {
    fun findByIdAndMember_Id(id: UUID, memberId: UUID): FarmingRecord?
    fun findByIdAndIsDeletedFalse(id: UUID): FarmingRecord?
    fun existsByFarm_Id(farmId: UUID): Boolean
    fun existsByMember_IdAndFarm_IdAndCrop_Id(memberId: UUID, farmId: UUID, cropId: UUID): Boolean
}
