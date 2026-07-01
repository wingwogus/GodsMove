package com.godsmove

import com.godsmove.application.coaching.rag.RagProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.godsmove"])
@EnableConfigurationProperties(RagProperties::class)
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
