package com.chamchamcham.domain.coaching

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CoachingFeedbackRepository : JpaRepository<CoachingFeedback, UUID> {
    fun findByFeedbackTypeAndRecord_IdAndSourceRevision(
        feedbackType: FeedbackType,
        recordId: UUID,
        sourceRevision: Long,
    ): CoachingFeedback?

    fun findAllByFeedbackTypeAndRecord_IdAndStatusIn(
        feedbackType: FeedbackType,
        recordId: UUID,
        statuses: Collection<CoachingFeedbackStatus>,
    ): List<CoachingFeedback>

    fun findTopByFeedbackTypeAndRecord_IdAndStatusOrderByUpdatedAtDesc(
        feedbackType: FeedbackType,
        recordId: UUID,
        status: CoachingFeedbackStatus,
    ): CoachingFeedback?

    fun findByIdAndMember_Id(id: UUID, memberId: UUID): CoachingFeedback?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feedback from CoachingFeedback feedback where feedback.id = :id and feedback.member.id = :memberId")
    fun findByIdAndMemberIdForUpdate(
        @Param("id") id: UUID,
        @Param("memberId") memberId: UUID,
    ): CoachingFeedback?
}
