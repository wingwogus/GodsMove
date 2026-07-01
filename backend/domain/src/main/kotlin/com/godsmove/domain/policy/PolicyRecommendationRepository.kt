package com.godsmove.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicyRecommendationRepository : JpaRepository<PolicyRecommendation, UUID>
