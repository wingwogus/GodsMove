package com.chamchamcham.domain.farm

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmRepository : JpaRepository<Farm, UUID> {
    fun findByOwner_Id(ownerId: UUID): List<Farm>
}
