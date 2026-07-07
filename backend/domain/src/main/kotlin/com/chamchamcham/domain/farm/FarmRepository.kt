package com.chamchamcham.domain.farm

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmRepository : JpaRepository<Farm, UUID> {
    fun findByIdAndOwner_Id(id: UUID, ownerId: UUID): Farm?
}
