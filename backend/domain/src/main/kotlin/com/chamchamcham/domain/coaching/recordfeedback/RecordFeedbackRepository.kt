package com.chamchamcham.domain.coaching.recordfeedback

import com.chamchamcham.domain.farming.FarmingRecord
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RecordFeedbackRepository : JpaRepository<RecordFeedback, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from FarmingRecord record where record.id = :recordId")
    fun findRecordByIdForFeedbackUpdate(
        @Param("recordId") recordId: UUID,
    ): FarmingRecord?

    fun findByRecord_IdAndSourceRevision(
        recordId: UUID,
        sourceRevision: Long,
    ): RecordFeedback?

    fun findTopByRecord_IdOrderBySourceRevisionDesc(recordId: UUID): RecordFeedback?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByRecord_IdOrderBySourceRevisionDesc(recordId: UUID): List<RecordFeedback>

    fun findByIdAndMember_Id(id: UUID, memberId: UUID): RecordFeedback?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feedback from RecordFeedback feedback where feedback.id = :id and feedback.member.id = :memberId")
    fun findByIdAndMemberIdForUpdate(
        @Param("id") id: UUID,
        @Param("memberId") memberId: UUID,
    ): RecordFeedback?
}
