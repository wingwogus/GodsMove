package com.chamchamcham.domain.community

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.media.UploadedMedia
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
    name = "community_post_media",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_community_post_media_uploaded_media", columnNames = ["uploaded_media_id"])
    ]
)
class CommunityPostMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val post: CommunityPost,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_media_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val uploadedMedia: UploadedMedia,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int
) : BaseTimeEntity()
