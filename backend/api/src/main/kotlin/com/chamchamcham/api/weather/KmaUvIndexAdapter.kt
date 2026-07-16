package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.UvIndexPort
import com.chamchamcham.application.weather.WeatherLocation
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component

/**
 * 생활기상지수(getUVIdxV5) 어댑터.
 *
 * areaNo는 시군구 단위 코드여야 한다(실측, 계획 §1): PNU 19자리 = 법정동코드(10, 그 중 앞 5자리가
 * 시도(2)+시군구(3)) + 필지구분(1) + 본번(4) + 부번(4)이다. 법정동 코드(pnu 앞 10자리)를 그대로
 * 쓰면 99 "검색결과가 없습니다"가 오고, 앞 5자리에 "00000"을 채운 시군구 코드라야 정상 응답한다.
 *
 * pnu가 null인 농지(프론트 VWorld 지적도 조회가 실패했거나 온보딩이 그 값을 못 받은 경우)는
 * areaNo를 만들 수 없어 자외선 지수를 아예 얻을 수 없다 — 이 경우 호출 없이 null을 반환한다.
 */
@Component
class KmaUvIndexAdapter(
    private val kmaApiClient: KmaApiClient,
    private val kmaBaseTime: KmaBaseTime,
    private val properties: KmaProperties
) : UvIndexPort {

    override fun fetch(location: WeatherLocation): Int? {
        val areaNo = location.pnu?.takeIf { it.length >= AREA_CODE_LENGTH }
            ?.take(AREA_CODE_LENGTH)
            ?.plus("00000")
            ?: return null
        val time = kmaBaseTime.resolveUv()

        val items = kmaApiClient.getItems(
            properties.baseUrl.uv,
            "getUVIdxV5",
            linkedMapOf("areaNo" to areaNo, "time" to time, "numOfRows" to "10"),
            JsonNode::class.java
        )

        return items.firstOrNull()?.get("h0")?.asText()?.toIntOrNull()
    }

    companion object {
        private const val AREA_CODE_LENGTH = 5
    }
}
