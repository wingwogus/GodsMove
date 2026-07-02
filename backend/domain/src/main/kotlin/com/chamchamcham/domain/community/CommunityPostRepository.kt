package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostRepository : JpaRepository<CommunityPost, UUID>
