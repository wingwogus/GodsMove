package com.chamchamcham.domain.farm

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmRepository : JpaRepository<Farm, UUID> {
    fun findByOwnerId(ownerId: UUID): List<Farm>
    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Farm?
}
