package com.chamchamcham.domain.voice

import com.chamchamcham.domain.common.BaseTimeEntity
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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.UUID

@Entity
@Table(name = "voice_record_turn")
class VoiceRecordTurn(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val session: VoiceRecordSession,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val role: VoiceTurnRole,

    @Column(nullable = false, columnDefinition = "text")
    val content: String,

    @Column(name = "extracted_fields", columnDefinition = "text")
    val extractedFields: String? = null,
) : BaseTimeEntity()
