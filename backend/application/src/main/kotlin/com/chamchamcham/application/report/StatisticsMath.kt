package com.chamchamcham.application.report

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object StatisticsMath {
    fun percentage(numerator: Int, denominator: Int): BigDecimal? =
        denominator.takeIf { it > 0 }?.let {
            BigDecimal(numerator).multiply(BigDecimal("100"))
                .divide(BigDecimal(it), 2, RoundingMode.HALF_UP)
        }

    fun percentage(numerator: BigDecimal, denominator: BigDecimal): BigDecimal? =
        denominator.takeIf { it.compareTo(BigDecimal.ZERO) != 0 }?.let {
            numerator.multiply(BigDecimal("100"))
                .divide(it, 2, RoundingMode.HALF_UP)
        }

    fun average(values: List<BigDecimal>): BigDecimal? =
        values.takeIf { it.isNotEmpty() }
            ?.sumOf { it }
            ?.divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP)

    fun averageIntervalDays(dates: List<LocalDate>): BigDecimal? {
        val sorted = dates.distinct().sorted()
        if (sorted.size < 2) return null
        return average(
            sorted.zipWithNext { first, second ->
                BigDecimal(ChronoUnit.DAYS.between(first, second))
            },
        )
    }
}
