package com.godsmove.domain.coaching

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CoachingFeedbackRepository : JpaRepository<CoachingFeedback, UUID>
