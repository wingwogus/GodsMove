package com.chamchamcham.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MemberCropRepository : JpaRepository<MemberCrop, UUID> {
    fun countByMemberId(memberId: UUID): Long
    fun findByMemberId(memberId: UUID): List<MemberCrop>

    @Query(
        """
        select mc
        from MemberCrop mc
        join fetch mc.crop
        where mc.member.id = :memberId
        """
    )
    fun findAllWithCropByMemberId(
        @Param("memberId") memberId: UUID
    ): List<MemberCrop>

    @Query(
        """
        select mc
        from MemberCrop mc
        join fetch mc.crop
        where mc.member.id = :memberId
          and mc.farm.id = :farmId
        """
    )
    fun findAllWithCropByMemberIdAndFarmId(
        @Param("memberId") memberId: UUID,
        @Param("farmId") farmId: UUID
    ): List<MemberCrop>

    fun deleteByMemberIdAndFarmId(memberId: UUID, farmId: UUID)
}
