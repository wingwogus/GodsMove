package com.chamchamcham.api.weather

/**
 * 중기기온 지점코드(taRegId) -> 중기육상예보 구역코드(regId) 매핑.
 *
 * 기상청은 두 API의 지역 체계를 다르게 쓴다. getMidTa는 시/군 단위 지점코드를 받지만,
 * getMidLandFcst는 아래 광역 단위 구역코드 10개만 받는다.
 *
 * 이 매핑을 예보구역정보 API의 regUp을 거슬러 올라가 구하려는 시도는 **원리적으로 실패한다.**
 * 육상 구역코드는 지점코드의 조상이 아니라 최상위(11000000) 아래의 형제 가지이기 때문이다:
 *
 *     11B10101 서울 -> 11B10100 서울(국지) -> 11B10000 서울(광역) -> 11000000 육상
 *     11B00000 서울.인천.경기 ------------------------------------> 11000000 육상
 *
 * 실제로 getMidLandFcst(regId=11B10000)은 03 NO_DATA다(실측).
 *
 * 대신 taRegId 앞 4자리가 이미 광역 구분을 담고 있다 — B1 서울 / B2 경기, C1 충북 / C2 충남,
 * D1 영서 / D2 영동, F1 전북 / F2 전남, H1 경북 / H2 경남. 그걸 그대로 쓴다.
 *
 * 아래 10개 코드는 전부 실호출로 00을 확인했다(2026-07-16).
 */
object MidTermLandRegion {
    private val BY_TA_REG_ID_PREFIX = mapOf(
        // 서해5도(백령/연평/소청)는 인천광역시 옹진군이라 서울.인천.경기에 속한다.
        // 자체 코드 11A00000은 예보구역으로는 존재하지만 getMidLandFcst에선 03이다(실측).
        "11A0" to "11B00000",
        "11B1" to "11B00000",
        "11B2" to "11B00000",
        "11C1" to "11C10000",
        "11C2" to "11C20000",
        "11D1" to "11D10000",
        "11D2" to "11D20000",
        // 울릉도.독도는 경상북도 울릉군이라 대구.경북에 속한다. 중기육상예보에 별도 구역이 없고,
        // 자체 코드 11E00000은 getMidLandFcst에서 03이다(실측).
        "11E0" to "11H10000",
        "11F1" to "11F10000",
        "11F2" to "11F20000",
        "11G0" to "11G00000",
        "11H1" to "11H10000",
        "11H2" to "11H20000"
    )

    /** 매핑되지 않는 taRegId면 null. 그 지점은 중기 육상예보(날씨 상태)를 얻을 수 없다. */
    fun of(taRegId: String): String? = BY_TA_REG_ID_PREFIX[taRegId.take(PREFIX_LENGTH)]

    private const val PREFIX_LENGTH = 4
}
