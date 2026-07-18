package com.chamchamcham.domain.member

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MemberRepository: JpaRepository<Member, UUID> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId")
    fun findByIdForUpdate(@Param("memberId") memberId: UUID): Member?

    @Modifying(clearAutomatically = true)
    @Query("delete from Member m where m.id = :memberId")
    fun hardDeleteById(@Param("memberId") memberId: UUID): Int
}
