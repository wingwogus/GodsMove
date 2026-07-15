package com.chamchamcham.application.config

import com.chamchamcham.application.coaching.common.RagProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(RagProperties::class)
class AsyncConfig {
    @Bean(name = [COACHING_TASK_EXECUTOR], defaultCandidate = false)
    fun coachingTaskExecutor(properties: RagProperties): ThreadPoolTaskExecutor {
        val execution = properties.execution
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = execution.corePoolSize
            maxPoolSize = execution.maxPoolSize
            setQueueCapacity(execution.queueCapacity)
            setThreadNamePrefix("coaching-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        }
    }

    companion object {
        const val COACHING_TASK_EXECUTOR = "coachingTaskExecutor"
    }
}
