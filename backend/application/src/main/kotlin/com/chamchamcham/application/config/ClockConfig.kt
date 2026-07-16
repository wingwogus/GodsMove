package com.chamchamcham.application.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * 기상청 API는 KST 기준으로만 동작한다. 컨테이너의 JVM 기본 zone은 보통 UTC라서,
 * `LocalDate.now()`/`LocalDateTime.now()`를 zone 없이 호출하면 KST 09시 이전(=UTC 기준 전날)에
 * base_date·캐시 키 등이 전부 조용히 하루 어긋난다. 그래서 시각이 필요한 모든 코드는 이 `Clock`
 * 빈을 주입받아야 하고, zone 없는 `now()` 호출은 금지한다.
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
