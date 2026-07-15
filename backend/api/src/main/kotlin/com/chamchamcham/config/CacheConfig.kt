package com.chamchamcham.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration

/**
 * 캐시 활성화. 스펙(TTL/최대 크기)은 application.yml의 `spring.cache.caffeine.spec`에 둔다.
 *
 * 지금 캐시를 쓰는 건 기상청 호출뿐이다. 기상청은 인증키당 하루 10,000회 한도가 있는데,
 * 캐시가 없으면 호출량이 사용자 수에 비례해 홈 1회=3콜 기준 사용자 330명쯤에서 한도를 친다.
 * 캐시가 있으면 호출량이 격자 수 × 발표 횟수로 바뀌어 사용자 수와 무관해진다.
 *
 * 저장소로 Caffeine(로컬 힙)을 쓴다. 서버가 여러 대가 되면 캐시가 대수만큼 복제되어 기상청
 * 호출량도 그만큼 늘어나므로, 그때는 이 파일 없이 CacheManager 빈만 Redis로 갈아끼우면 된다
 * (서비스 코드는 Spring Cache 추상화만 보므로 바뀌지 않는다).
 */
@Configuration
@EnableCaching
class CacheConfig
