package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PlantingRecordRepository : JpaRepository<PlantingRecord, UUID> {
    fun findByRecord_Id(recordId: UUID): PlantingRecord?
    fun deleteByRecord(record: FarmingRecord)
}
