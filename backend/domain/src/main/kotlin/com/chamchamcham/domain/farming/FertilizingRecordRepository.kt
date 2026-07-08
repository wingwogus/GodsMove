package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FertilizingRecordRepository : JpaRepository<FertilizingRecord, UUID> {
    fun findByRecord_Id(recordId: UUID): FertilizingRecord?
    fun deleteByRecord(record: FarmingRecord)
}
