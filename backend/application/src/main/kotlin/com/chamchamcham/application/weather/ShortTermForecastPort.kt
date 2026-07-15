package com.chamchamcham.application.weather

/**
 * 단기예보. 발표시각마다 응답에 담기는 날짜가 달라서, 하나의 API를 용도별로 나눠 부른다.
 */
interface ShortTermForecastPort {
    /** 최신 발표. 현재 하늘상태·강수확률과 내일 이후 예보에 쓴다. 자료가 없으면 null. */
    fun fetchLatest(location: WeatherLocation): ShortTermForecast?

    /**
     * 당일 최저/최고 전용 조회.
     *
     * 기상청 단기예보는 지나간 예보 시각을 응답에서 빼는데, 당일 최저는 06시·최고는 15시
     * 항목에 실린다. 그래서 당일 최저/최고를 둘 다 싣는 발표는 02시 하나뿐이고,
     * 최신 발표를 그대로 쓰면 하루 21시간 동안 당일 최저/최고를 얻지 못한다.
     * 그 사실이 이 메서드가 [fetchLatest]와 따로 존재하는 유일한 이유다.
     */
    fun fetchTodayRange(location: WeatherLocation): DailyForecast?
}
