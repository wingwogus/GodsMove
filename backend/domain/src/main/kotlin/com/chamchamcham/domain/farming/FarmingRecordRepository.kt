package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmingRecordRepository : JpaRepository<FarmingRecord, UUID> {
    fun findByIdAndMember_Id(id: UUID, memberId: UUID): FarmingRecord?
}
