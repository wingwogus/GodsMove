package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityCommentRepository : JpaRepository<CommunityComment, UUID>
