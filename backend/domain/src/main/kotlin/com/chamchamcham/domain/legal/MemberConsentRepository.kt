package com.chamchamcham.domain.legal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberConsentRepository : JpaRepository<MemberConsent, UUID>
