package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FarmingRecordMediaRepository : JpaRepository<FarmingRecordMedia, UUID> {
    fun findByRecord_Id(recordId: UUID): List<FarmingRecordMedia>

    @Query(
        """
        select distinct m.record.id
        from FarmingRecordMedia m
        where m.record.id in :recordIds
        """
    )
    fun findDistinctRecordIdsByRecordIdIn(recordIds: Collection<UUID>): Set<UUID>

    fun deleteByRecord(record: FarmingRecord)
}
