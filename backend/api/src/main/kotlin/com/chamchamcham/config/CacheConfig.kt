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
 * 저장소는 Caffeine(로컬 힙)이고, `spring.cache.type: caffeine`으로 **명시**해야 한다 —
 * 인증이 쓰는 spring-boot-starter-data-redis 때문에 클래스패스에 Redis가 있어서, 비워두면
 * 자동설정이 Redis를 먼저 골라간다.
 *
 * 서버가 여러 대가 되면 캐시가 대수만큼 복제되어 기상청 호출량도 그만큼 늘어난다. 그때 Redis로
 * 옮기려면 타입만 바꾸는 걸로는 부족하고 값 직렬화를 따로 붙여야 한다(기본 JDK 직렬화는 기상청
 * 응답 DTO를 NotSerializableException으로 터뜨린다). 캐시 대상이 제네릭 List<T>라 Jackson
 * 직렬화도 타입 정보를 함께 저장해야 한다.
 */
@Configuration
@EnableCaching
class CacheConfig
