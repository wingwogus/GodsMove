package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class KmaBaseTimeResolverTest {

    @Test
    fun `초단기실황은 40분 이전이면 직전 정시를 사용한다`() {
        val result = KmaBaseTimeResolver.resolveNcst(LocalDateTime.of(2026, 7, 8, 10, 39))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260708", baseTime = "0900"))
    }

    @Test
    fun `초단기실황은 40분 이후면 현재 정시를 사용한다`() {
        val result = KmaBaseTimeResolver.resolveNcst(LocalDateTime.of(2026, 7, 8, 10, 40))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260708", baseTime = "1000"))
    }

    @Test
    fun `초단기실황은 자정 직후 전날 23시로 롤오버한다`() {
        val result = KmaBaseTimeResolver.resolveNcst(LocalDateTime.of(2026, 7, 8, 0, 10))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260707", baseTime = "2300"))
    }

    @Test
    fun `초단기예보는 45분 이전이면 직전 시각의 30분 발표를 사용한다`() {
        val result = KmaBaseTimeResolver.resolveFcst(LocalDateTime.of(2026, 7, 8, 10, 44))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260708", baseTime = "0930"))
    }

    @Test
    fun `초단기예보는 45분 이후면 현재 시각의 30분 발표를 사용한다`() {
        val result = KmaBaseTimeResolver.resolveFcst(LocalDateTime.of(2026, 7, 8, 10, 45))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260708", baseTime = "1030"))
    }

    @Test
    fun `초단기예보는 자정 30분 이전이면 전날 23시 30분으로 롤오버한다`() {
        val result = KmaBaseTimeResolver.resolveFcst(LocalDateTime.of(2026, 7, 8, 0, 30))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260707", baseTime = "2330"))
    }

    @Test
    fun `동네예보는 첫 발표시각 전이면 전날 23시를 사용한다`() {
        val result = KmaBaseTimeResolver.resolveVilageFcst(LocalDateTime.of(2026, 7, 11, 2, 9))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260710", baseTime = "2300"))
    }

    @Test
    fun `단기예보는 발표시각 10분 전이면 직전 스케줄을 사용한다`() {
        val result = KmaBaseTimeResolver.resolveVilageFcst(LocalDateTime.of(2026, 7, 8, 17, 9))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260708", baseTime = "1400"))
    }

    @Test
    fun `단기예보는 발표시각 10분 이후면 해당 스케줄을 사용한다`() {
        val result = KmaBaseTimeResolver.resolveVilageFcst(LocalDateTime.of(2026, 7, 8, 17, 10))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260708", baseTime = "1700"))
    }

    @Test
    fun `단기예보는 스케줄 시각 사이에도 가장 최근 스케줄을 사용한다`() {
        val result = KmaBaseTimeResolver.resolveVilageFcst(LocalDateTime.of(2026, 7, 8, 16, 59))

        assertThat(result).isEqualTo(KmaBaseDateTime(baseDate = "20260708", baseTime = "1400"))
    }

    @Test
    fun `자외선지수는 정확히 경계 시각이면 그대로 사용한다`() {
        val result = KmaBaseTimeResolver.resolveUvIdx(LocalDateTime.of(2026, 7, 8, 12, 0))

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 7, 8, 12, 0))
    }

    @Test
    fun `자외선지수는 경계 사이 시각이면 직전 3시간 경계로 내림한다`() {
        val result = KmaBaseTimeResolver.resolveUvIdx(LocalDateTime.of(2026, 7, 8, 14, 37))

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 7, 8, 12, 0))
    }

    @Test
    fun `자외선지수는 스케줄에 00시가 포함되어 있어 자정 이후에도 전날로 롤오버하지 않는다`() {
        val result = KmaBaseTimeResolver.resolveUvIdx(LocalDateTime.of(2026, 7, 8, 1, 0))

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 7, 8, 0, 0))
    }

    @Test
    fun `중기예보는 06시 20분 이후면 당일 06시 발표를 사용한다`() {
        val result = KmaBaseTimeResolver.resolveMidFcst(LocalDateTime.of(2026, 7, 8, 10, 0))

        assertThat(result).isEqualTo("202607080600")
    }

    @Test
    fun `중기예보는 18시 20분 이후면 당일 18시 발표를 사용한다`() {
        val result = KmaBaseTimeResolver.resolveMidFcst(LocalDateTime.of(2026, 7, 8, 20, 0))

        assertThat(result).isEqualTo("202607081800")
    }

    @Test
    fun `중기예보는 06시 20분 이전이면 전날 18시 발표로 롤오버한다`() {
        val result = KmaBaseTimeResolver.resolveMidFcst(LocalDateTime.of(2026, 7, 8, 6, 10))

        assertThat(result).isEqualTo("202607071800")
    }

    @Test
    fun `중기예보는 자정 직후에도 전날 18시 발표로 롤오버한다`() {
        val result = KmaBaseTimeResolver.resolveMidFcst(LocalDateTime.of(2026, 7, 8, 0, 5))

        assertThat(result).isEqualTo("202607071800")
    }
}
