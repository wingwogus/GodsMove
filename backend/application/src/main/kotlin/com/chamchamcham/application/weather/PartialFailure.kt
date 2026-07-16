package com.chamchamcham.application.weather

/**
 * 어떤 선택 소스가 빠졌는지 모으는 값 객체.
 *
 * 부분실패 판정은 여기 한 곳에서만 한다 — 여러 클래스에 흩어지면
 * "왜 uvIndex가 partial에 없지?"를 추적할 수 없다(계획 §5).
 */
data class PartialFailure(val missing: List<String>) {
    val degraded: Boolean get() = missing.isNotEmpty()

    companion object {
        fun of(vararg entries: Pair<String, Boolean>): PartialFailure =
            PartialFailure(entries.filter { it.second }.map { it.first })
    }
}
