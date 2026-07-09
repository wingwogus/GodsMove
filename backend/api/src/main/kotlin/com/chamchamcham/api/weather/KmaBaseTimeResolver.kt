package com.chamchamcham.api.weather

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class KmaBaseDateTime(val baseDate: String, val baseTime: String)

/**
 * 기상청 초단기실황/초단기예보의 base_date, base_time 계산(순수 함수).
 *
 * - 초단기실황(getUltraSrtNcst): 매시 정시 발표, 약 40분 이후 제공.
 * - 초단기예보(getUltraSrtFcst): 매시 30분 발표, 약 45분 이후 제공.
 */
object KmaBaseTimeResolver {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private const val NCST_AVAILABLE_MINUTE = 40
    private const val FCST_AVAILABLE_MINUTE = 45

    fun resolveNcst(now: LocalDateTime): KmaBaseDateTime {
        val base = if (now.minute < NCST_AVAILABLE_MINUTE) now.minusHours(1) else now
        return KmaBaseDateTime(
            baseDate = base.format(DATE_FORMAT),
            baseTime = "%02d00".format(base.hour)
        )
    }

    fun resolveFcst(now: LocalDateTime): KmaBaseDateTime {
        val base = if (now.minute < FCST_AVAILABLE_MINUTE) now.minusHours(1) else now
        return KmaBaseDateTime(
            baseDate = base.format(DATE_FORMAT),
            baseTime = "%02d30".format(base.hour)
        )
    }
}
