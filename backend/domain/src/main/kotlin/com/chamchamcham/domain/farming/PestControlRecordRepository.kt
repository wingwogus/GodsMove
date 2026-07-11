package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PestControlRecordRepository : JpaRepository<PestControlRecord, UUID> {
    fun findByRecord_Id(recordId: UUID): PestControlRecord?
    fun deleteByRecord(record: FarmingRecord)
}
