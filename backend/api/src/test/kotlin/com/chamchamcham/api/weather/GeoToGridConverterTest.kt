package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeoToGridConverterTest {

    @Test
    fun `서울시청 위경도를 기상청 격자 좌표로 변환한다`() {
        val grid = GeoToGridConverter.convert(latitude = 37.5665, longitude = 126.9780)

        assertThat(grid).isEqualTo(KmaGrid(nx = 60, ny = 127))
    }

    @Test
    fun `동일 입력은 항상 동일한 격자를 반환한다`() {
        val first = GeoToGridConverter.convert(35.1796, 129.0756)
        val second = GeoToGridConverter.convert(35.1796, 129.0756)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `서로 다른 좌표는 서로 다른 격자를 반환한다`() {
        val seoul = GeoToGridConverter.convert(37.5665, 126.9780)
        val busan = GeoToGridConverter.convert(35.1796, 129.0756)

        assertThat(seoul).isNotEqualTo(busan)
    }
}
