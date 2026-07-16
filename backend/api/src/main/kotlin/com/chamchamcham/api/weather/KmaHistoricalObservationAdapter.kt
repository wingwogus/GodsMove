package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.DailyWeather
import com.chamchamcham.application.weather.HistoricalObservationPort
import com.chamchamcham.application.weather.WeatherLocation
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * ASOS 일자료(getWthrDataList) 어댑터. 과거 날짜 실측의 유일한 소스다.
 *
 * 오늘은 "전날 자료까지 제공됩니다"(99)로 거부되고 KmaApiClient가 이미 빈 리스트로 흡수하므로
 * 여기선 그냥 null이 된다(오늘은 애초에 이 포트를 호출하지 않는 게 상위 계약이다).
 */
@Component
class KmaHistoricalObservationAdapter(
    private val kmaApiClient: KmaApiClient,
    private val properties: KmaProperties
) : HistoricalObservationPort {

    override fun fetch(location: WeatherLocation, date: LocalDate): DailyWeather? {
        val station = NearestAsosStationResolver.resolve(location.latitude, location.longitude)
        val dateText = date.format(DATE_FORMAT)

        val items = kmaApiClient.getItems(
            properties.baseUrl.asos,
            "getWthrDataList",
            linkedMapOf(
                "dataCd" to "ASOS",
                "dateCd" to "DAY",
                "startDt" to dateText,
                "endDt" to dateText,
                "stnIds" to station.id,
                "numOfRows" to "10"
            ),
            AsosDailyItemDto::class.java
        )
        val item = items.firstOrNull() ?: return null

        // 기록에 붙는 값이라 반쪽짜리를 남기지 않는다: 최저/최고 중 하나라도 없거나 파싱 불가면 실패.
        val minTemperature = item.minTa?.toDoubleOrNull()?.roundToInt() ?: return null
        val maxTemperature = item.maxTa?.toDoubleOrNull()?.roundToInt() ?: return null

        // sumRn=''(무강수)는 toDoubleOrNull()로 null이 되고, AsosConditionMapper는 sumRn==null을
        // "비 아님"으로 취급한다(실측, 계획 §1) — 의도된 동작이다.
        val condition = AsosConditionMapper.of(
            avgTca = item.avgTca?.toDoubleOrNull(),
            sumRn = item.sumRn?.toDoubleOrNull()
        )

        return DailyWeather(
            date = date,
            condition = condition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature
        )
    }

    private data class AsosDailyItemDto(
        @JsonProperty("minTa") val minTa: String? = null,
        @JsonProperty("maxTa") val maxTa: String? = null,
        @JsonProperty("avgTca") val avgTca: String? = null,
        @JsonProperty("sumRn") val sumRn: String? = null
    )

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
