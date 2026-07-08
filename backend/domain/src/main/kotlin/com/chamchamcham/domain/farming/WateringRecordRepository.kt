package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WateringRecordRepository : JpaRepository<WateringRecord, UUID> {
    fun findByRecord_Id(recordId: UUID): WateringRecord?
    fun deleteByRecord(record: FarmingRecord)
}
