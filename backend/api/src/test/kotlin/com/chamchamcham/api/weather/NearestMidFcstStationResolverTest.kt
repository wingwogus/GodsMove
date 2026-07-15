package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NearestMidFcstStationResolverTest {

    private val seoul = MidFcstStation(taRegId = "11B10101", landRegId = "11B10000", name = "서울", latitude = 37.56609444, longitude = 126.9774167)
    private val busan = MidFcstStation(taRegId = "11H20201", landRegId = "11H20000", name = "부산", latitude = 35.104683, longitude = 129.032013)
    private val jeju = MidFcstStation(taRegId = "11G00201", landRegId = "11G00000", name = "제주", latitude = 33.486275, longitude = 126.4979528)
    private val fixture = listOf(seoul, busan, jeju)

    @Test
    fun `서울 인근 좌표는 서울 지점을 반환한다`() {
        val result = NearestMidFcstStationResolver.resolve(latitude = 37.5665, longitude = 126.9780, stations = fixture)

        assertThat(result).isEqualTo(seoul)
    }

    @Test
    fun `부산 인근 좌표는 부산 지점을 반환한다`() {
        val result = NearestMidFcstStationResolver.resolve(latitude = 35.1796, longitude = 129.0756, stations = fixture)

        assertThat(result).isEqualTo(busan)
    }

    @Test
    fun `제주 인근 좌표는 제주 지점을 반환하고 taRegId와 landRegId를 모두 갖는다`() {
        val result = NearestMidFcstStationResolver.resolve(latitude = 33.4996, longitude = 126.5312, stations = fixture)

        assertThat(result).isEqualTo(jeju)
        assertThat(result.taRegId).isEqualTo("11G00201")
        assertThat(result.landRegId).isEqualTo("11G00000")
    }

    @Test
    fun `기본 인자는 전체 중기예보 지점 목록을 사용한다`() {
        val result = NearestMidFcstStationResolver.resolve(latitude = 37.5665, longitude = 126.9780)

        assertThat(result.taRegId).isEqualTo("11B10101")
    }
}
