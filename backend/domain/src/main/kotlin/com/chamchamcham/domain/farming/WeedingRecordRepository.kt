package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WeedingRecordRepository : JpaRepository<WeedingRecord, UUID> {
    fun findByRecord_Id(recordId: UUID): WeedingRecord?
    fun deleteByRecord(record: FarmingRecord)
}
