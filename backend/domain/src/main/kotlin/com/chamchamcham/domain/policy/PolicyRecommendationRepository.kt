package com.chamchamcham.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicyRecommendationRepository : JpaRepository<PolicyRecommendation, UUID> {
    fun existsByMember_IdAndSourceSyncJob_Id(memberId: UUID, sourceSyncJobId: UUID): Boolean
    fun deleteByMember_Id(memberId: UUID)
}
