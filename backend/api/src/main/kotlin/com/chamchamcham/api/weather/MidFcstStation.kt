package com.chamchamcham.api.weather

/**
 * 기상청 중기예보 지역코드 지점.
 * @param taRegId 중기기온(getMidTa) 조회에 쓰는 지역코드(시/군 단위)
 * @param landRegId 중기육상예보(getMidLandFcst) 조회에 쓰는 지역코드(도 단위) — taRegId의 상위(regUp)를
 *   두 단계 거슬러 올라간 지역코드를 미리 계산해 저장한 값이다.
 * @param name 지점명(시/군)
 * @param latitude 지점 위도
 * @param longitude 지점 경도
 */
data class MidFcstStation(
    val taRegId: String,
    val landRegId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
