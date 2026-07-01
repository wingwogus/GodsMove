package com.godsmove.domain.member

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberRepository: JpaRepository<Member, UUID> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
}
