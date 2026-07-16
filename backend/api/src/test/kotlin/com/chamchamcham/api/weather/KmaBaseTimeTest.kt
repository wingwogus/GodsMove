package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * `Clock.fixed`로 시각을 고정해 "입력 시각 -> 출력 base_time" 경계를 검증한다.
 * 경계표는 계획(pure-sauteeing-perlis.md) §9 회귀 테스트 표를 그대로 옮긴 것이다.
 */
class KmaBaseTimeTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun kmaBaseTimeAt(dateTime: LocalDateTime): KmaBaseTime {
        val instant = dateTime.atZone(zone).toInstant()
        return KmaBaseTime(Clock.fixed(instant, zone))
    }

    // ---------- resolveTodayRange / resolveLatest / resolveMidFcst 경계표 ----------

    @Test
    fun `00시 05분 - 어제 2300 최신 어제2300 중기 어제1800`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 0, 5))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260714", "2300"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260714", "2300"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607141800")
    }

    @Test
    fun `02시 09분 - todayRange latest 모두 어제 2300, 중기는 어제1800`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 2, 9))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260714", "2300"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260714", "2300"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607141800")
    }

    @Test
    fun `02시 10분 - todayRange latest 모두 오늘 0200, 중기는 여전히 어제1800`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 2, 10))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607141800")
    }

    @Test
    fun `06시 19분 - todayRange는 여전히 0200, latest는 0500, 중기는 아직 어제1800`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 6, 19))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "0500"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607141800")
    }

    @Test
    fun `06시 20분 - 중기가 오늘0600으로 넘어간다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 6, 20))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "0500"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607150600")
    }

    @Test
    fun `10시 - todayRange는 여전히 0200이다(회귀 방지 핵심)`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 10, 0))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "0800"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607150600")
    }

    @Test
    fun `14시 10분 - todayRange는 여전히 0200이다(옛 버그가 17시 이후로 오진했던 경계)`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 14, 10))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "1400"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607150600")
    }

    @Test
    fun `18시 19분 - 중기는 아직 오늘0600`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 18, 19))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "1700"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607150600")
    }

    @Test
    fun `18시 20분 - 중기가 오늘1800으로 넘어간다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 18, 20))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "1700"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607151800")
    }

    @Test
    fun `21시 50분 - todayRange는 여전히 0200이다(회귀 방지 핵심)`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 21, 50))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "2000"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607151800")
    }

    @Test
    fun `23시 10분 - todayRange는 여전히 0200, latest는 2300`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 23, 10))

        assertThat(kmaBaseTime.resolveTodayRange()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
        assertThat(kmaBaseTime.resolveLatest()).isEqualTo(KmaBaseDateTime("20260715", "2300"))
        assertThat(kmaBaseTime.resolveMidFcst()).isEqualTo("202607151800")
    }

    // ---------- resolveNcst ----------

    @Test
    fun `초단기실황은 39분이면 직전 정시를 사용한다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 10, 39))

        assertThat(kmaBaseTime.resolveNcst()).isEqualTo(KmaBaseDateTime("20260715", "0900"))
    }

    @Test
    fun `초단기실황은 40분이면 현재 정시를 사용한다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 10, 40))

        assertThat(kmaBaseTime.resolveNcst()).isEqualTo(KmaBaseDateTime("20260715", "1000"))
    }

    @Test
    fun `초단기실황은 00시 30분이면 전날 23시로 넘어간다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 0, 30))

        assertThat(kmaBaseTime.resolveNcst()).isEqualTo(KmaBaseDateTime("20260714", "2300"))
    }

    // ---------- resolveUv ----------

    @Test
    fun `자외선지수는 00시 00분이면 00시로 내림한다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 0, 0))

        assertThat(kmaBaseTime.resolveUv()).isEqualTo("2026071500")
    }

    @Test
    fun `자외선지수는 02시 59분이면 00시로 내림한다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 2, 59))

        assertThat(kmaBaseTime.resolveUv()).isEqualTo("2026071500")
    }

    @Test
    fun `자외선지수는 03시 00분이면 그대로 03시를 사용한다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 3, 0))

        assertThat(kmaBaseTime.resolveUv()).isEqualTo("2026071503")
    }

    @Test
    fun `자외선지수는 23시 59분이면 21시로 내림한다`() {
        val kmaBaseTime = kmaBaseTimeAt(LocalDateTime.of(2026, 7, 15, 23, 59))

        assertThat(kmaBaseTime.resolveUv()).isEqualTo("2026071521")
    }

    @Test
    fun `KST 고정 Clock을 fixed Instant로 만들어도 시각 계산이 KST 기준으로 된다`() {
        // UTC 기준 07-14 18:05 == KST 07-15 03:05. UTC로 그대로 계산했다면 07-14가 나와야 한다.
        val utcInstant = LocalDateTime.of(2026, 7, 14, 18, 5).toInstant(ZoneOffset.UTC)
        val kmaBaseTime = KmaBaseTime(Clock.fixed(utcInstant, zone))

        assertThat(kmaBaseTime.resolveNcst()).isEqualTo(KmaBaseDateTime("20260715", "0200"))
    }
}
