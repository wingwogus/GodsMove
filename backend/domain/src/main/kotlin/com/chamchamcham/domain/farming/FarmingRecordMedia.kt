package com.chamchamcham.domain.farming

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
    name = "farming_record_media",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_farming_record_media_uploaded_media", columnNames = ["uploaded_media_id"])
    ]
)
class FarmingRecordMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val record: FarmingRecord,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_media_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val uploadedMedia: UploadedMedia,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int
) : BaseTimeEntity()
