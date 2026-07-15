package com.chamchamcham.api.weather

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 기상청 발표시각(base_date/base_time/tmFc/time) 계산. 전부 순수 로직이지만 "지금"이 필요해서
 * Clock을 주입받는다(인자 없는 now() 금지 — Asia/Seoul 고정 Clock 전제, ClockConfig 참고).
 * base_date/base_time 쌍은 기존 [KmaBaseDateTime]을 그대로 재사용한다.
 */
@Component
class KmaBaseTime(private val clock: Clock) {

    /**
     * 초단기실황(getUltraSrtNcst). 매시 정시 발표, 40분 후 제공되므로 40분 이전이면 직전 정시를 쓴다.
     */
    fun resolveNcst(): KmaBaseDateTime {
        val now = now()
        val base = if (now.minute < NCST_AVAILABLE_MINUTE) now.minusHours(1) else now
        return KmaBaseDateTime(baseDate = base.format(DATE_FORMAT), baseTime = "%02d00".format(base.hour))
    }

    /**
     * 단기예보(getVilageFcst)의 최신 발표(02/05/08/11/14/17/20/23시, +10분 버퍼).
     * 현재 하늘상태·강수확률·D1~D4 예보에 쓴다(계획 §2.1 latest()).
     */
    fun resolveLatest(): KmaBaseDateTime {
        val now = now()
        val availableHour = VILAGE_FCST_SCHEDULE_HOURS.lastOrNull { hour ->
            now.hour > hour || (now.hour == hour && now.minute >= VILAGE_FCST_AVAILABLE_MINUTE)
        }
        val base = if (availableHour != null) {
            now.withHour(availableHour)
        } else {
            now.minusDays(1).withHour(VILAGE_FCST_SCHEDULE_HOURS.last())
        }
        return KmaBaseDateTime(baseDate = base.format(DATE_FORMAT), baseTime = "%02d00".format(base.hour))
    }

    /**
     * 단기예보(getVilageFcst) 중 당일 TMN/TMX가 둘 다 실리는 유일한 발표(0200)만 쓴다.
     * 실측(계획 §1)상 TMN은 05시 발표부터, TMX는 14시 발표부터 당일 항목이 사라지므로
     * "최신 발표"를 그대로 쓰면 하루 21시간 동안 오늘 최저/최고가 없다. 02:10 이전엔 그날의
     * 0200 발표가 아직 나오지 않았으므로 전날 2300 발표를 쓴다(전날 2300이 오늘 TMN/TMX를
     * 준다는 것도 실측 확인됨). 하루 종일 같은 키(baseDate, "0200")라 캐시 키가 격자당 하루 1개가 된다.
     */
    fun resolveTodayRange(): KmaBaseDateTime {
        val now = now()
        val isAvailable = now.hour > TODAY_RANGE_HOUR ||
            (now.hour == TODAY_RANGE_HOUR && now.minute >= TODAY_RANGE_AVAILABLE_MINUTE)
        return if (isAvailable) {
            KmaBaseDateTime(baseDate = now.format(DATE_FORMAT), baseTime = "%02d00".format(TODAY_RANGE_HOUR))
        } else {
            val yesterday = now.minusDays(1)
            KmaBaseDateTime(
                baseDate = yesterday.format(DATE_FORMAT),
                baseTime = "%02d00".format(VILAGE_FCST_SCHEDULE_HOURS.last())
            )
        }
    }

    /**
     * 중기예보(getMidLandFcst/getMidTa)의 tmFc(스케줄 06/18시, +20분 버퍼).
     * 기상청은 최신 발표만 유지한다 — 과거 tmFc는 03 NO_DATA(실측 확인). 그래서 항상 최신만 조회한다.
     */
    fun resolveMidFcst(): String {
        val now = now()
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

    /**
     * 생활기상지수(getUVIdxV5)의 time. 3시간 경계(00/03/06/09/12/15/18/21)로 내림한다.
     * 스케줄에 0시가 있어 자정 직후도 같은 날 00시로 내려가고 전날로 롤오버하지 않는다.
     * 공식 확인된 제공 지연이 없어 버퍼를 두지 않는다(제공 지연 미확인).
     */
    fun resolveUv(): String {
        val now = now()
        val boundaryHour = UV_SCHEDULE_HOURS.last { hour -> now.hour >= hour }
        return now.withHour(boundaryHour).format(UV_FORMAT)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TM_FC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        private val UV_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")

        private const val NCST_AVAILABLE_MINUTE = 40

        private val VILAGE_FCST_SCHEDULE_HOURS = listOf(2, 5, 8, 11, 14, 17, 20, 23)
        private const val VILAGE_FCST_AVAILABLE_MINUTE = 10

        private const val TODAY_RANGE_HOUR = 2
        private const val TODAY_RANGE_AVAILABLE_MINUTE = 10

        private val MID_FCST_SCHEDULE_HOURS = listOf(6, 18)
        private const val MID_FCST_AVAILABLE_MINUTE = 20

        private val UV_SCHEDULE_HOURS = listOf(0, 3, 6, 9, 12, 15, 18, 21)
    }
}
