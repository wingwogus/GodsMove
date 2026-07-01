package com.godsmove.domain.member

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExternalIdentityRepository : JpaRepository<ExternalIdentity, UUID> {
    fun findByProviderAndProviderSubject(
        provider: AuthProvider,
        providerSubject: String
    ): ExternalIdentity?
}
