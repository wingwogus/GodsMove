package com.godsmove.domain.voice

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VoiceRecordSessionRepository : JpaRepository<VoiceRecordSession, UUID>
