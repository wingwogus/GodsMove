package com.godsmove.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberCropRepository : JpaRepository<MemberCrop, UUID>
