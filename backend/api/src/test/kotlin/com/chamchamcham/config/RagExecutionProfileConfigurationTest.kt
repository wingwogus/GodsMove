package com.chamchamcham.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

class RagExecutionProfileConfigurationTest {
    @Test
    fun `all profiles expose bounded coaching execution settings`() {
        val profiles = listOf(
            "application-local.yml",
            "application-dev.yml",
            "application-prod.yml",
            "application-test.yml",
        )

        profiles.forEach { profile ->
            val properties = YamlPropertySourceLoader()
                .load(profile, ClassPathResource(profile))
                .single()

            assertThat(properties.getProperty("rag.execution.core-pool-size"))
                .describedAs("%s coaching core pool size", profile)
                .isEqualTo("\${RAG_EXECUTOR_CORE_SIZE:2}")
            assertThat(properties.getProperty("rag.execution.max-pool-size"))
                .describedAs("%s coaching max pool size", profile)
                .isEqualTo("\${RAG_EXECUTOR_MAX_SIZE:4}")
            assertThat(properties.getProperty("rag.execution.queue-capacity"))
                .describedAs("%s coaching queue capacity", profile)
                .isEqualTo("\${RAG_EXECUTOR_QUEUE_CAPACITY:32}")
            assertThat(properties.getProperty("rag.execution.pending-timeout-millis"))
                .describedAs("%s coaching pending timeout", profile)
                .isEqualTo("\${RAG_PENDING_TIMEOUT_MILLIS:120000}")
            assertThat(properties.getProperty("rag.execution.scan-interval-millis"))
                .describedAs("%s coaching scan interval", profile)
                .isEqualTo("\${RAG_PENDING_SCAN_INTERVAL_MILLIS:60000}")
        }
    }
}
