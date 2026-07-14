package com.chamchamcham.api.voice

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OpenAiRealtimeProperties::class)
class OpenAiRealtimeConfig
