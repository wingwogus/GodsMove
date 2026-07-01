package com.godsmove.domain.farm

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmRepository : JpaRepository<Farm, UUID>
