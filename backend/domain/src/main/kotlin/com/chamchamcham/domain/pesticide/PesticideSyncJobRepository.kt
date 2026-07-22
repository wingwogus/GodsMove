package com.chamchamcham.domain.pesticide

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PesticideSyncJobRepository : JpaRepository<PesticideSyncJob, UUID>
