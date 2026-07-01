package com.godsmove.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmingRecordRepository : JpaRepository<FarmingRecord, UUID>
