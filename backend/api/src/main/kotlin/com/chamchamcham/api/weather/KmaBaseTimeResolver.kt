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
    private val VILAGE_FCST_SCHEDULE_HOURS = listOf(2, 5, 8, 11, 14, 17, 20, 23)
    private const val VILAGE_FCST_AVAILABLE_MINUTE = 10
    private val UV_IDX_SCHEDULE_HOURS = listOf(0, 3, 6, 9, 12, 15, 18, 21)
    private val MID_FCST_SCHEDULE_HOURS = listOf(6, 18)
    private const val MID_FCST_AVAILABLE_MINUTE = 20
    private val TM_FC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

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

    /**
     * 단기예보(getVilageFcst)의 base_date, base_time 계산(순수 함수).
     * 발표 스케줄: 02/05/08/11/14/17/20/23시, 약 10분 이후 제공.
     */
    fun resolveVilageFcst(now: LocalDateTime): KmaBaseDateTime {
        val availableHour = VILAGE_FCST_SCHEDULE_HOURS.lastOrNull { hour ->
            now.hour > hour || (now.hour == hour && now.minute >= VILAGE_FCST_AVAILABLE_MINUTE)
        }
        val base = if (availableHour != null) {
            now.withHour(availableHour)
        } else {
            now.minusDays(1).withHour(VILAGE_FCST_SCHEDULE_HOURS.last())
        }
        return KmaBaseDateTime(
            baseDate = base.format(DATE_FORMAT),
            baseTime = "%02d00".format(base.hour)
        )
    }

    /**
     * 생활기상지수(자외선지수, getUVIdxV5)의 time(yyyyMMddHH) 계산(순수 함수).
     * 3시간 단위(00/03/06/09/12/15/18/21)로 내림한다. 공식 확인된 제공 지연 시간이 없어
     * 별도 버퍼 없이 가장 최근 3시간 경계로만 내림한다. 스케줄에 00시가 포함되어 있어
     * 자정을 지난 시각도 같은 날짜의 00시로 내려가며, 전날로 롤오버하지 않는다.
     */
    fun resolveUvIdx(now: LocalDateTime): LocalDateTime {
        val boundaryHour = UV_IDX_SCHEDULE_HOURS.last { hour -> now.hour >= hour }
        return now.withHour(boundaryHour).withMinute(0).withSecond(0).withNano(0)
    }

    /**
     * 중기예보(getMidLandFcst/getMidTa)의 tmFc(yyyyMMddHHmm) 계산(순수 함수).
     * 발표 스케줄: 06/18시 1일 2회. 제공 지연 시간이 공식 문서로 확인되지 않아, 단기예보(10분)보다
     * 넉넉하게 20분 버퍼를 둔다.
     */
    fun resolveMidFcst(now: LocalDateTime): String {
        val availableHour = MID_FCST_SCHEDULE_HOURS.lastOrNull { hour ->
            now.hour > hour || (now.hour == hour && now.minute >= MID_FCST_AVAILABLE_MINUTE)
        }
        val base = if (availableHour != null) {
            now.withHour(availableHour)
        } else {
            now.minusDays(1).withHour(MID_FCST_SCHEDULE_HOURS.last())
        }
        return base.withMinute(0).withSecond(0).withNano(0).format(TM_FC_FORMAT)
    }
}
