package com.chamchamcham.application.weather

/**
 * 날씨 상태 코드. 국제 표준(WMO 4677 등)은 기상청 SKY/PTY와 1:1 대응이 안 되고
 * 프론트가 쓸 수 있는 것도 아니라서, 국내 사실상의 표준인 기상청 SKY/PTY 코드
 * 체계를 그대로 1:1로 감싼 것이다(계획 §5).
 *
 * PTY 코드 공간은 소스마다 다르다: SHOWER(PTY=4)는 단기예보(getVilageFcst)에만,
 * DRIZZLE/DRIZZLE_SNOW/SNOW_FLURRY(PTY=5,6,7)는 초단기실황(getUltraSrtNcst)에만
 * 존재한다. 값이 겹치지 않아 하나의 enum으로 합쳐도 안전하다.
 *
 * 중기예보 wf(자유 텍스트)와 ASOS(전운량/강수량 근사)도 이 enum으로 수렴한다.
 */
enum class WeatherCondition(val text: String) {
    /** SKY=1. getVilageFcst */
    CLEAR("맑음"),

    /** SKY=3. getVilageFcst */
    PARTLY_CLOUDY("구름많음"),

    /** SKY=4. getVilageFcst */
    CLOUDY("흐림"),

    /** PTY=1. 실황(getUltraSrtNcst) · getVilageFcst */
    RAIN("비"),

    /** PTY=2. 실황(getUltraSrtNcst) · getVilageFcst */
    RAIN_SNOW("비/눈"),

    /** PTY=3. 실황(getUltraSrtNcst) · getVilageFcst */
    SNOW("눈"),

    /** PTY=4. getVilageFcst 전용 */
    SHOWER("소나기"),

    /** PTY=5. 실황(getUltraSrtNcst) 전용 */
    DRIZZLE("빗방울"),

    /** PTY=6. 실황(getUltraSrtNcst) 전용 */
    DRIZZLE_SNOW("빗방울눈날림"),

    /** PTY=7. 실황(getUltraSrtNcst) 전용 */
    SNOW_FLURRY("눈날림"),

    /** 매핑 불가 */
    UNKNOWN("정보없음")
}
