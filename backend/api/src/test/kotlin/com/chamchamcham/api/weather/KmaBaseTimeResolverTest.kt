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
}
