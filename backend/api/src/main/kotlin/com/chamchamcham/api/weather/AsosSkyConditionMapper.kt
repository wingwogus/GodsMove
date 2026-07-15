package com.chamchamcham.api.weather

/**
 * ASOS 일자료(평균 전운량 avgTca, 일강수량 sumRn)로부터 하늘상태 문자열을 근사하는 순수 함수.
 * `KmaWeatherProvider.skyConditionOf`(SKY/PTY 코드 기반)와 어휘(맑음/구름많음/흐림/비)는
 * 맞추되, 입력 필드가 달라 별도 함수로 둔다.
 */
object AsosSkyConditionMapper {
    fun of(avgTca: Double?, sumRn: Double?): String {
        if (sumRn != null && sumRn > 0.0) return "비"

        return when {
            avgTca == null -> "정보없음"
            avgTca <= 2.0 -> "맑음"
            avgTca <= 7.0 -> "구름많음"
            else -> "흐림"
        }
    }
}
