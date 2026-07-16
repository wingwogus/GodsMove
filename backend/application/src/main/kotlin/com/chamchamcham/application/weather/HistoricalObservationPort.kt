package com.chamchamcham.application.weather

import java.time.LocalDate

/**
 * 과거 날짜의 실측(ASOS 일자료). 기록 화면이 고른 지난 날짜에 쓴다.
 *
 * 기상청이 전날 자료까지만 주므로 오늘은 여기서 조회할 수 없다. 오늘은 단기예보 쪽을 쓴다.
 * 해당 날짜 자료가 없으면 null이다.
 */
interface HistoricalObservationPort {
    fun fetch(location: WeatherLocation, date: LocalDate): DailyWeather?
}
