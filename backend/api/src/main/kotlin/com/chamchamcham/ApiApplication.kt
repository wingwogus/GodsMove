package com.chamchamcham

import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.voice.VoiceSessionProperties
import com.chamchamcham.config.OpenClawProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.chamchamcham"])
@EnableConfigurationProperties(RagProperties::class, OpenClawProperties::class, VoiceSessionProperties::class)
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
