package com.chamchamcham.application.config

import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.policy.sync.PolicySyncAsyncRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

class AsyncConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration::class.java))
        .withUserConfiguration(AsyncConfig::class.java)

    @Test
    fun `coaching execution defaults are bounded`() {
        val execution = RagProperties().execution

        assertThat(execution.corePoolSize).isEqualTo(2)
        assertThat(execution.maxPoolSize).isEqualTo(4)
        assertThat(execution.queueCapacity).isEqualTo(32)
        assertThat(execution.pendingTimeoutMillis).isEqualTo(240_000L)
        assertThat(execution.scanIntervalMillis).isEqualTo(60_000L)
    }

    @Test
    fun `coaching executor is bounded and discards saturated work for timeout recovery`() {
        contextRunner.run { context ->
            assertThat(context).hasBean("coachingTaskExecutor")
            val executor = context.getBean("coachingTaskExecutor", ThreadPoolTaskExecutor::class.java)

            assertThat(executor.corePoolSize).isEqualTo(2)
            assertThat(executor.maxPoolSize).isEqualTo(4)
            assertThat(executor.queueCapacity).isEqualTo(32)
            assertThat(executor.threadPoolExecutor.rejectedExecutionHandler)
                .isInstanceOf(ThreadPoolExecutor.DiscardPolicy::class.java)
        }
    }

    @Test
    fun `unqualified async work keeps the Boot default executor`() {
        contextRunner.run { context ->
            assertThat(context).hasBean("applicationTaskExecutor")
            assertThat(context).hasBean("coachingTaskExecutor")

            val defaultExecutor = context.getBean("applicationTaskExecutor")
            val coachingExecutor = context.getBean("coachingTaskExecutor")
            val asyncConfigurer = context.getBean(AsyncConfigurer::class.java)

            assertThat(defaultExecutor).isNotSameAs(coachingExecutor)
            assertThat(asyncConfigurer.asyncExecutor).isSameAs(defaultExecutor)
        }

        val policyRun = PolicySyncAsyncRunner::class.java.getDeclaredMethod(
            "run",
            java.util.UUID::class.java,
        )
        assertThat(policyRun.getAnnotation(org.springframework.scheduling.annotation.Async::class.java).value)
            .isEmpty()
    }
}
