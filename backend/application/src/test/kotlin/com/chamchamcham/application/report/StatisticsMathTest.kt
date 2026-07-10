package com.chamchamcham.application.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class StatisticsMathTest {
    @Test
    fun `percentage and average use scale two half up`() {
        assertThat(StatisticsMath.percentage(1, 3)).isEqualByComparingTo("33.33")
        assertThat(StatisticsMath.average(listOf(BigDecimal("1"), BigDecimal("2"))))
            .isEqualByComparingTo("1.50")
    }

    @Test
    fun `average interval uses distinct dates`() {
        assertThat(
            StatisticsMath.averageIntervalDays(
                listOf(date(1), date(1), date(4), date(10)),
            ),
        ).isEqualByComparingTo("4.50")
    }

    @Test
    fun `empty values return null`() {
        assertThat(StatisticsMath.percentage(0, 0)).isNull()
        assertThat(StatisticsMath.average(emptyList())).isNull()
        assertThat(StatisticsMath.averageIntervalDays(emptyList())).isNull()
    }

    private fun date(day: Int): LocalDate = LocalDate.of(2026, 1, day)
}
