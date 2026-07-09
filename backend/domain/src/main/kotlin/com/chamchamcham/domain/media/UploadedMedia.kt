package com.chamchamcham.domain.media

import com.chamchamcham.domain.common.BaseTimeEntity
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
import java.util.UUID

@Entity
@Table(name = "uploaded_media")
class UploadedMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false)
    val owner: Member,

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    val mediaType: UploadedMediaType,

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 32)
    val usageType: UploadedMediaUsageType,

    @Column(name = "file_url", nullable = false, length = 2048)
    val fileUrl: String,

    @Column(name = "cloudinary_public_id", nullable = false, length = 255)
    val cloudinaryPublicId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: UploadedMediaStatus = UploadedMediaStatus.TEMP,
) : BaseTimeEntity() {
    fun markAttached() {
        status = UploadedMediaStatus.ATTACHED
    }

    fun markDeleted() {
        status = UploadedMediaStatus.DELETED
    }

    fun isAttachable(): Boolean = status == UploadedMediaStatus.TEMP
}
