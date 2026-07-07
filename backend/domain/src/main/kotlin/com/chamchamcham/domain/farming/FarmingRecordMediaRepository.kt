package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmingRecordMediaRepository : JpaRepository<FarmingRecordMedia, UUID> {
    fun findByRecord_Id(recordId: UUID): List<FarmingRecordMedia>
}
