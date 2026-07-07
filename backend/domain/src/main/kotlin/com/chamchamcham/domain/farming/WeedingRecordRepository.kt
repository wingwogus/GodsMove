package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WeedingRecordRepository : JpaRepository<WeedingRecord, UUID> {
    fun findByRecordId(recordId: UUID): WeedingRecord?
}
