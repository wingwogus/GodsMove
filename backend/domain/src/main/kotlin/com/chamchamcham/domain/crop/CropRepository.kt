package com.chamchamcham.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CropRepository : JpaRepository<Crop, UUID> {
    fun findAllByOrderByNameAscExternalNoAsc(): List<Crop>
    fun findByExternalNoIn(externalNos: Collection<Int>): List<Crop>
}
