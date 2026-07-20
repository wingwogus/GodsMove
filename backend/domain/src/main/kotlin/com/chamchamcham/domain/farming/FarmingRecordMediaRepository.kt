package com.chamchamcham.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmingRecordMediaRepository : JpaRepository<FarmingRecordMedia, UUID> {
    fun findByRecord_Id(recordId: UUID): List<FarmingRecordMedia>
    fun findByUploadedMediaIdIn(mediaIds: Collection<UUID>): List<FarmingRecordMedia>
    fun deleteByRecord(record: FarmingRecord)
}
