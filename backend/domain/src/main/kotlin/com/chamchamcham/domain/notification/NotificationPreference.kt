package com.chamchamcham.domain.notification

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.UUID

@Entity
@Table(
    name = "notification_preference",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_preference_member_channel_topic",
            columnNames = ["member_id", "channel", "topic"]
        )
    ]
)
class NotificationPreference(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val member: Member,

    @Column(nullable = false, length = 32)
    val channel: String,

    @Column(nullable = false, length = 64)
    val topic: String,

    @Column(nullable = false)
    val enabled: Boolean = true,
) : BaseTimeEntity()
