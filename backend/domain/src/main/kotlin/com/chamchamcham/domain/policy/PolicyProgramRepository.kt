package com.chamchamcham.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface PolicyProgramRepository : JpaRepository<PolicyProgram, UUID> {
    fun findBySourceAndExternalIdAndSourceYear(
        source: PolicySource,
        externalId: String,
        sourceYear: String
    ): PolicyProgram?

    @Query(
        """
        select p
        from PolicyProgram p
        where p.lastSyncedJob.id = :syncJobId
          and p.sourceYear = :sourceYear
          and p.detailSynced = true
          and p.recommendable = true
          and (p.applyEndsOn is null or p.applyEndsOn >= :today)
        """
    )
    fun findRecommendableCandidates(
        @Param("syncJobId") syncJobId: UUID,
        @Param("sourceYear") sourceYear: String,
        @Param("today") today: LocalDate
    ): List<PolicyProgram>

    fun findByIdAndDetailSyncedTrueAndRecommendableTrue(id: UUID): PolicyProgram?
}
