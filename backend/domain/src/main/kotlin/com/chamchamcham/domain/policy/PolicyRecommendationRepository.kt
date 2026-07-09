package com.chamchamcham.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface PolicyRecommendationRepository : JpaRepository<PolicyRecommendation, UUID> {
    @Query(
        """
        select r.policyProgram.id
        from PolicyRecommendation r
        where r.member.id = :memberId
          and r.policyProgram.source = :source
          and r.policyProgram.sourceYear = :sourceYear
        """
    )
    fun findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
        @Param("memberId") memberId: UUID,
        @Param("source") source: PolicySource,
        @Param("sourceYear") sourceYear: String
    ): List<UUID>

    @Query(
        """
        select max(r.createdAt)
        from PolicyRecommendation r
        where r.member.id = :memberId
          and r.policyProgram.source = :source
          and r.policyProgram.sourceYear = :sourceYear
        """
    )
    fun findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
        @Param("memberId") memberId: UUID,
        @Param("source") source: PolicySource,
        @Param("sourceYear") sourceYear: String
    ): LocalDateTime?

    @Modifying
    @Query(
        """
        delete from PolicyRecommendation r
        where r.member.id = :memberId
          and r.policyProgram.id in (
              select p.id
              from PolicyProgram p
              where p.source = :source
                and p.sourceYear = :sourceYear
          )
        """
    )
    fun deleteByMemberIdAndPolicyProgramSourceAndSourceYear(
        @Param("memberId") memberId: UUID,
        @Param("source") source: PolicySource,
        @Param("sourceYear") sourceYear: String
    ): Int

}
