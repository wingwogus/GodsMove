package com.chamchamcham.application.weather

/**
 * 자외선 지수.
 *
 * 선택 소스라 실패하면 null이고 요청은 계속된다. 다만 그 사실은 응답의 partial에 드러난다 —
 * 이전 구현은 실패를 조용히 삼켜서 자외선이 항상 null인 걸 아무도 몰랐다.
 */
interface UvIndexPort {
    fun fetch(location: WeatherLocation): Int?
}
