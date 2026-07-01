package com.godsmove.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkTypeRepository : JpaRepository<WorkType, UUID>
