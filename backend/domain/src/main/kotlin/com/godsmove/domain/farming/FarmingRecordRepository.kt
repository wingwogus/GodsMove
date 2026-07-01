package com.godsmove.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.EntityGraph
import java.util.UUID

interface FarmingRecordRepository : JpaRepository<FarmingRecord, UUID> {
    @EntityGraph(attributePaths = ["workType"])
    fun findByIdAndMember_Id(id: UUID, memberId: UUID): FarmingRecord?
}
