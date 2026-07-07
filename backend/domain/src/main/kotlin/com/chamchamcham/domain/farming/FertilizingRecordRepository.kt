package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FertilizingRecordRepository : JpaRepository<FertilizingRecord, UUID> {
    fun findByRecordId(recordId: UUID): FertilizingRecord?
}
