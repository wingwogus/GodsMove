package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MidTermLandRegionTest {

    /**
     * getMidLandFcst가 받아주는 구역코드 전부. 2026-07-16에 15개 후보를 실호출해 이 10개만
     * resultCode=00이고 나머지(11B10000/11B20000/11A00000/11E00000/11000000)는 03임을 확인했다.
     */
    private val validLandRegIds = setOf(
        "11B00000", "11C10000", "11C20000", "11D10000", "11D20000",
        "11F10000", "11F20000", "11G00000", "11H10000", "11H20000"
    )

    @Test
    fun `모든 중기예보 지점이 유효한 육상 구역코드로 매핑된다`() {
        val unmapped = MidFcstStations.ALL.filter { MidTermLandRegion.of(it.taRegId) == null }

        assertThat(unmapped)
            .describedAs("매핑되지 않는 지점이 있으면 그 지역은 주간예보 5일째 날씨 상태를 얻지 못한다")
            .isEmpty()
    }

    /**
     * 이전 구현이 regUp을 두 단계 거슬러 올라가 만든 표는 178행 중 41행이 getMidLandFcst가
     * 거부하는 코드였다(11B20000 31행, 11B10000 3행, 11A00000 3행, 11E00000 2행, 11000000 1행).
     * 그 결함을 잡는 회귀 테스트다.
     */
    @Test
    fun `매핑 결과는 기상청이 받아주는 10개 코드 중 하나다`() {
        val produced = MidFcstStations.ALL.mapNotNull { MidTermLandRegion.of(it.taRegId) }.toSet()

        assertThat(produced).isSubsetOf(validLandRegIds)
    }

    @ParameterizedTest
    @CsvSource(
        "11B10101, 11B00000",  // 서울 — 2단계 위(11B10000)를 쓰면 03이 된다
        "11B20201, 11B00000",  // 인천
        "11A00101, 11B00000",  // 백령도 — 인천광역시 옹진군
        "11E00101, 11H10000",  // 울릉도 — 경상북도 울릉군
        "11E00102, 11H10000",  // 독도
        "11G00800, 11G00000",  // 추자도 — 제주
        "11C10301, 11C10000",  // 충북
        "11C20401, 11C20000",  // 충남
        "11D10301, 11D10000",  // 강원영서
        "11D20501, 11D20000",  // 강원영동
        "11F10201, 11F10000",  // 전북
        "11F20501, 11F20000",  // 전남
        "11H10701, 11H10000",  // 대구
        "11H20201, 11H20000"   // 부산
    )
    fun `taRegId 접두 4자리로 육상 구역코드를 정한다`(taRegId: String, expected: String) {
        assertThat(MidTermLandRegion.of(taRegId)).isEqualTo(expected)
    }

    @Test
    fun `알 수 없는 접두사는 null이다`() {
        assertThat(MidTermLandRegion.of("99Z00000")).isNull()
        assertThat(MidTermLandRegion.of("")).isNull()
    }
}
