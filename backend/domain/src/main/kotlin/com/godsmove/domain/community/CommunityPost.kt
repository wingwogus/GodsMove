package com.godsmove.domain.community

import com.godsmove.domain.crop.Crop
import com.godsmove.domain.farming.FarmingRecord
import com.godsmove.domain.member.Member
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
@Table(name = "community_post")
class CommunityPost(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_member_id", nullable = false)
    val author: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farming_record_id")
    val farmingRecord: FarmingRecord? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    val crop: Crop? = null,

    @Column(name = "post_type", nullable = false, length = 32)
    val postType: String,

    @Column(nullable = false, length = 255)
    val title: String,

    @Column(nullable = false, columnDefinition = "text")
    val body: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val status: CommunityPostStatus = CommunityPostStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
