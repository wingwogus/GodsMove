package com.chamchamcham.application.weather

/**
 * 현재 실측값(기온·습도·풍속·강수형태).
 *
 * 유일한 필수 소스다. 실패하면 요청 전체가 503으로 실패한다 — 현재 기온 없이는 홈도 상세도
 * 의미가 없다. 나머지 소스는 전부 선택이라 실패해도 해당 필드만 비운다.
 */
interface CurrentObservationPort {
    fun fetch(location: WeatherLocation): CurrentObservation
}
