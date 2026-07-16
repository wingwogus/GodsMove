package com.chamchamcham.api.weather

/**
 * 기상청 중기예보 지점.
 *
 * 중기육상예보(getMidLandFcst) 구역코드는 여기 담지 않는다 — 지점마다 값을 저장하면 규칙과
 * 어긋날 수 있어서, [MidTermLandRegion]이 taRegId에서 계산한다.
 *
 * @param taRegId 중기기온(getMidTa) 조회에 쓰는 지역코드(시/군 단위)
 * @param name 지점명(시/군)
 * @param latitude 지점 위도
 * @param longitude 지점 경도
 */
data class MidFcstStation(
    val taRegId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
