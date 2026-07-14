package com.chamchamcham.domain.pesticide

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PesticideRepository : JpaRepository<Pesticide, UUID> {
    fun findByItemNameAndBrandName(itemName: String, brandName: String): Pesticide?
}
