package com.chamchamcham.domain.community

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
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
    @OnDelete(action = OnDeleteAction.CASCADE)
    val author: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    var crop: Crop,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farming_record_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var farmingRecord: FarmingRecord? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 32)
    var postType: CommunityPostType,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(nullable = false, columnDefinition = "text")
    var body: String,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,
) : BaseTimeEntity() {
    fun update(
        crop: Crop,
        farmingRecord: FarmingRecord?,
        postType: CommunityPostType,
        title: String,
        body: String
    ) {
        this.crop = crop
        this.farmingRecord = farmingRecord
        this.postType = postType
        this.title = title
        this.body = body
    }

    fun softDelete() {
        isDeleted = true
    }
}
