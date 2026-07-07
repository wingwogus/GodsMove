package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PlantingRecordRepository : JpaRepository<PlantingRecord, UUID> {
    fun findByRecordId(recordId: UUID): PlantingRecord?
}
