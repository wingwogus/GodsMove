package com.chamchamcham.application.weather

import java.time.LocalDate

/**
 * 중기예보. 단기예보가 닿지 않는 날짜를 채운다.
 *
 * 단기예보는 오늘+3일까지만 주므로 5일 예보의 마지막 날은 여기서만 얻을 수 있다.
 * 다만 중기예보는 단기예보와 겹치는 오늘+3일을 아예 주지 않고, 18시 발표는 오늘+4일도
 * 주지 않는다(그때는 단기예보 쪽에 오늘+4일이 들어온다). 그래서 이건 항상 폴백이다 —
 * 단기예보가 그 날짜를 주면 더 정확한 그쪽을 쓴다.
 */
interface MidTermForecastPort {
    fun fetch(location: WeatherLocation, date: LocalDate): DailyForecast?
}
