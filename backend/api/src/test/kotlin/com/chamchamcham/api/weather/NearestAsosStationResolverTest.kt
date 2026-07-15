package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NearestAsosStationResolverTest {

    private val seoul = AsosStation(id = "108", name = "서울", latitude = 37.5714, longitude = 126.9658)
    private val busan = AsosStation(id = "159", name = "부산", latitude = 35.1047, longitude = 129.0320)
    private val jeju = AsosStation(id = "184", name = "제주", latitude = 33.5141, longitude = 126.5297)
    private val fixture = listOf(seoul, busan, jeju)

    @Test
    fun `서울 인근 좌표는 서울 지점을 반환한다`() {
        val result = NearestAsosStationResolver.resolve(latitude = 37.5665, longitude = 126.9780, stations = fixture)

        assertThat(result).isEqualTo(seoul)
    }

    @Test
    fun `부산 인근 좌표는 부산 지점을 반환한다`() {
        val result = NearestAsosStationResolver.resolve(latitude = 35.1796, longitude = 129.0756, stations = fixture)

        assertThat(result).isEqualTo(busan)
    }

    @Test
    fun `제주 인근 좌표는 제주 지점을 반환한다`() {
        val result = NearestAsosStationResolver.resolve(latitude = 33.4996, longitude = 126.5312, stations = fixture)

        assertThat(result).isEqualTo(jeju)
    }

    @Test
    fun `기본 인자는 전체 ASOS 지점 목록을 사용한다`() {
        val result = NearestAsosStationResolver.resolve(latitude = 37.5665, longitude = 126.9780)

        assertThat(result.id).isEqualTo("108")
    }
}
