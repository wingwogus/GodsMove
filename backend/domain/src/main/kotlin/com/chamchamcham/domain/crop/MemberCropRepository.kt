package com.chamchamcham.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberCropRepository : JpaRepository<MemberCrop, UUID> {
    fun countByMember_Id(memberId: UUID): Long
}
