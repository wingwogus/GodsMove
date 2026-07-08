package com.chamchamcham.api.weather

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

data class KmaGrid(val nx: Int, val ny: Int)

/**
 * 위경도 -> 기상청 격자좌표(nx, ny) 변환.
 * 기상청 동네예보 Lambert Conformal Conic(LCC) 표준 파라미터를 사용하는 순수 함수.
 */
object GeoToGridConverter {
    private const val RE = 6371.00877 // 지구 반경(km)
    private const val GRID = 5.0 // 격자 간격(km)
    private const val SLAT1 = 30.0 // 표준위도 1
    private const val SLAT2 = 60.0 // 표준위도 2
    private const val OLON = 126.0 // 기준점 경도
    private const val OLAT = 38.0 // 기준점 위도
    private const val XO = 43 // 기준점 X좌표
    private const val YO = 136 // 기준점 Y좌표

    private const val DEGRAD = PI / 180.0

    fun convert(latitude: Double, longitude: Double): KmaGrid {
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD

        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        var ra = tan(PI * 0.25 + latitude * DEGRAD * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = longitude * DEGRAD - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = (ra * sin(theta) + XO + 0.5).toInt()
        val ny = (ro - ra * cos(theta) + YO + 0.5).toInt()
        return KmaGrid(nx, ny)
    }
}
