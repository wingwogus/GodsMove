package com.chamchamcham.application.weather

import java.time.LocalDate

/**
 * 하루치 예보.
 *
 * 최저/최고는 발표시각에 따라 응답에 없을 수 있어 nullable이다. 없으면 null로 두고
 * 남은 시간대 기온으로 지어내지 않는다 — 그 폴백이 이전 구현에서 하루 21시간 동안
 * 틀린 최저/최고를 조용히 내보낸 원인이었다.
 */
data class DailyForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val minTemperature: Int?,
    val maxTemperature: Int?
) {
    /**
     * 주간예보 카드가 쓸 만한 하루인지. 요구사항이 날마다 {상태, 온도, 요일}이라 온도가 없으면
     * 반쪽짜리다.
     *
     * 단기예보는 예보 창 가장자리 날짜를 이런 반쪽짜리로 준다 — 실측상 08시 발표의 +4일은
     * fcstTime=0000 슬롯 하나뿐이라 SKY만 있고 TMN/TMX가 없다. 그 반쪽짜리가 온도를 가진
     * 중기예보를 가리지 않도록 판정을 여기 한 곳에 둔다.
     */
    val hasTemperatureRange: Boolean
        get() = minTemperature != null && maxTemperature != null
}
