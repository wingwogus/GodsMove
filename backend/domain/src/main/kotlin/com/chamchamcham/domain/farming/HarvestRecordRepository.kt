package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HarvestRecordRepository : JpaRepository<HarvestRecord, UUID> {
    fun findByRecord_Id(recordId: UUID): HarvestRecord?
    fun findByRecord_IdIn(recordIds: Collection<UUID>): List<HarvestRecord>
    fun deleteByRecord(record: FarmingRecord)
}
