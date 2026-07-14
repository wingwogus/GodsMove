package com.chamchamcham.domain.pesticide

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PestRepository : JpaRepository<Pest, UUID> {
    fun findByName(name: String): Pest?
}
