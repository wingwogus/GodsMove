package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.MidTermForecastPort
import com.chamchamcham.application.weather.WeatherLocation
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 중기예보(getMidLandFcst/getMidTa) 어댑터. 단기예보가 닿지 않는 +4일 이후를 채운다.
 *
 * wfN/taMinN/taMaxN의 N은 tmFc 날짜 기준 +N일이다(실측, 계획 §1). 기상청이 과거 tmFc를
 * 03 NO_DATA로 막아 항상 최신 tmFc만 쓸 수 있고, 최신 tmFc 기준 N은 항상 4 또는 5로만
 * 나온다(N=3은 단기예보와 겹쳐 절대 제공되지 않는다).
 */
@Component
class KmaMidTermForecastAdapter(
    private val kmaApiClient: KmaApiClient,
    private val kmaBaseTime: KmaBaseTime,
    private val properties: KmaProperties
) : MidTermForecastPort {

    override fun fetch(location: WeatherLocation, date: LocalDate): DailyForecast? {
        val station = NearestMidFcstStationResolver.resolve(location.latitude, location.longitude)
        val tmFc = kmaBaseTime.resolveMidFcst()
        val tmFcDate = LocalDate.parse(tmFc.take(8), TM_FC_DATE_FORMAT)
        val n = ChronoUnit.DAYS.between(tmFcDate, date).toInt()

        // 중기예보 제공 범위(+3~+10일) 밖이면 호출 자체가 낭비다.
        if (n !in MID_FCST_MIN_OFFSET..MID_FCST_MAX_OFFSET) return null

        val taItems = kmaApiClient.getItems(
            properties.baseUrl.midFcst,
            "getMidTa",
            linkedMapOf("regId" to station.taRegId, "tmFc" to tmFc, "numOfRows" to "10"),
            JsonNode::class.java
        )
        // 기온과 육상은 지역 체계가 다르다. 기온은 시/군 지점코드를 그대로 쓰지만, 육상은 광역
        // 구역코드 10개만 받으므로 taRegId에서 계산한다(자세한 근거는 MidTermLandRegion).
        val landRegId = MidTermLandRegion.of(station.taRegId)
        val landNode = landRegId?.let {
            kmaApiClient.getItems(
                properties.baseUrl.midFcst,
                "getMidLandFcst",
                linkedMapOf("regId" to it, "tmFc" to tmFc, "numOfRows" to "10"),
                JsonNode::class.java
            ).firstOrNull()
        }

        val taNode = taItems.firstOrNull()

        val minTemperature = taNode?.get("taMin$n")?.asText()?.toIntOrNull()
        val maxTemperature = taNode?.get("taMax$n")?.asText()?.toIntOrNull()

        // 주간예보 카드는 낮 날씨를 보여주므로 Pm을 우선 쓴다.
        // n이 8~10이면 Am/Pm 구분 없이 wf8/wf9/wf10으로 오므로 마지막에 그것도 본다(실측 확인:
        // 응답 키가 ..., wf7Am, wf7Pm, wf8, wf9, wf10 이다).
        // 기상청은 값이 없는 날짜의 필드를 JSON null이 아니라 키 자체를 생략해서 보낸다(실측
        // 확인). 그래서 get()이 null을 돌려주고 이 ?: 체인이 그대로 동작한다.
        val wf = landNode?.get("wf${n}Pm")?.asText()
            ?: landNode?.get("wf${n}Am")?.asText()
            ?: landNode?.get("wf$n")?.asText()

        // 데이터를 지어내지 않는다: 온도와 wf가 둘 다 없으면 이 날짜는 조회 실패다.
        if (minTemperature == null && maxTemperature == null && wf == null) return null

        return DailyForecast(
            date = date,
            condition = MidTermWfConditionMapper.of(wf),
            minTemperature = minTemperature,
            maxTemperature = maxTemperature
        )
    }

    companion object {
        private val TM_FC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private const val MID_FCST_MIN_OFFSET = 3
        private const val MID_FCST_MAX_OFFSET = 10
    }
}
