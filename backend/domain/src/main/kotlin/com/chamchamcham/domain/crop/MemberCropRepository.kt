package com.chamchamcham.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberCropRepository : JpaRepository<MemberCrop, UUID> {
    fun countByMemberId(memberId: UUID): Long
    fun findByMemberId(memberId: UUID): List<MemberCrop>
    fun existsByMemberIdAndFarmIdAndCropId(memberId: UUID, farmId: UUID, cropId: UUID): Boolean
    fun deleteByMemberIdAndFarmId(memberId: UUID, farmId: UUID)
}
