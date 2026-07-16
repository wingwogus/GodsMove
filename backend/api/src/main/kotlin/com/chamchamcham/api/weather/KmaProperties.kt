package com.chamchamcham.api.weather

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 기상청 API 4개(단기예보/중기예보/ASOS/생활기상지수) 공통 설정 한 벌.
 * prefix가 `weather.kma`가 아니라 `kma`인 이유: 재설계 이전 코드가 아직
 * `weather.kma.*`를 쓰는 `WeatherKmaProperties`로 남아 있어(삭제는 나중 단계),
 * 같은 prefix를 쓰면 두 `@ConfigurationProperties`가 충돌한다.
 */
@ConfigurationProperties(prefix = "kma")
data class KmaProperties(
    val serviceKey: String,
    val connectTimeoutMillis: Int = 2000,
    val readTimeoutMillis: Int = 2000,
    val baseUrl: BaseUrl
) {
    data class BaseUrl(
        val vilageFcst: String,
        val midFcst: String,
        val asos: String,
        val uv: String
    )
}
