package com.chamchamcham.domain.media

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UploadedMediaRepository : JpaRepository<UploadedMedia, UUID> {
    @Query("select m.cloudinaryPublicId from UploadedMedia m where m.owner.id = :memberId")
    fun findCloudinaryPublicIdsByOwnerId(@Param("memberId") memberId: UUID): List<String>
}
