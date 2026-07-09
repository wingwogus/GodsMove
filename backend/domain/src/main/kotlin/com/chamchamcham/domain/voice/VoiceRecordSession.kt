package com.chamchamcham.domain.voice

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "voice_record_session")
class VoiceRecordSession(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_record_id")
    var draftRecord: FarmingRecord? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: VoiceSessionStatus,

    @Column(columnDefinition = "text")
    var transcript: String? = null,

    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    fun markWaitingConfirmation(transcript: String) {
        check(status == VoiceSessionStatus.CREATED) { "session $id is not in CREATED state" }
        this.transcript = transcript
        this.status = VoiceSessionStatus.WAITING_CONFIRMATION
    }

    fun confirm(record: FarmingRecord, confirmedAt: LocalDateTime) {
        check(status == VoiceSessionStatus.WAITING_CONFIRMATION) { "session $id is not in WAITING_CONFIRMATION state" }
        this.draftRecord = record
        this.confirmedAt = confirmedAt
        this.status = VoiceSessionStatus.COMPLETED
    }

    fun cancel() {
        check(status != VoiceSessionStatus.COMPLETED) { "session $id is already COMPLETED" }
        this.status = VoiceSessionStatus.CANCELLED
    }
}
