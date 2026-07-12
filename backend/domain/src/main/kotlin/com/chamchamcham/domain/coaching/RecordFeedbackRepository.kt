package com.chamchamcham.domain.coaching

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RecordFeedbackRepository : JpaRepository<RecordFeedback, UUID> {
    fun findByRecord_IdAndSourceRevision(
        recordId: UUID,
        sourceRevision: Long,
    ): RecordFeedback?

    fun findAllByRecord_IdAndStatusIn(
        recordId: UUID,
        statuses: Collection<RecordFeedbackStatus>,
    ): List<RecordFeedback>

    fun findTopByRecord_IdAndStatusOrderByUpdatedAtDesc(
        recordId: UUID,
        status: RecordFeedbackStatus,
    ): RecordFeedback?

    fun findByIdAndMember_Id(id: UUID, memberId: UUID): RecordFeedback?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feedback from RecordFeedback feedback where feedback.id = :id and feedback.member.id = :memberId")
    fun findByIdAndMemberIdForUpdate(
        @Param("id") id: UUID,
        @Param("memberId") memberId: UUID,
    ): RecordFeedback?
}
