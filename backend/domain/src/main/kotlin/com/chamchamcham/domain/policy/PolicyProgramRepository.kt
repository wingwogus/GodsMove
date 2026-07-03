package com.chamchamcham.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicyProgramRepository : JpaRepository<PolicyProgram, UUID>
