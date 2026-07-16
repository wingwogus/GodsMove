package com.chamchamcham.application.weather

/**
 * 날씨를 조회할 위치. Farm 엔티티에서 뽑아낸 값 객체다.
 *
 * 기상청 코드(격자 nx/ny, ASOS stnId, 중기예보 regId, 생활기상지수 areaNo)는 여기 담지 않는다.
 * 그 변환은 전부 어댑터 안에서 한다 — 그래야 application이 기상청을 모른다는 말이 참이 된다.
 */
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val roadAddress: String,
    val pnu: String?
)
