package com.chamchamcham.domain.pesticide

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PesticideApplicationRepository : JpaRepository<PesticideApplication, UUID> {
    fun findByPesticide_IdAndPest_IdAndCropName(pesticideId: UUID, pestId: UUID, cropName: String): PesticideApplication?

    @Query(
        """
        select distinct pa.pest
        from PesticideApplication pa
        where pa.pesticide.id = :pesticideId
        order by pa.pest.name asc
        """
    )
    fun findDistinctPestsByPesticideId(@Param("pesticideId") pesticideId: UUID): List<Pest>
}
