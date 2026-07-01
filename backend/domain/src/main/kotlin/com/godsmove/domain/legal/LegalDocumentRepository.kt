package com.godsmove.domain.legal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LegalDocumentRepository : JpaRepository<LegalDocument, UUID>
