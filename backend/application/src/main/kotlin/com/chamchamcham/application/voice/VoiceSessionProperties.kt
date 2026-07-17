package com.chamchamcham.application.voice

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 음성 세션 대화 한도. 오디오는 클라이언트 ↔ OpenAI 직결이라 백엔드가 대화 중간에 개입할 수
 * 없으므로, 이 값은 세션 생성 응답으로 클라이언트에 전달되어 클라이언트가 강제한다.
 */
@ConfigurationProperties(prefix = "app.voice-session")
data class VoiceSessionProperties(
    val maxRounds: Int = 20,
    val maxDurationSeconds: Int = 330,
)
