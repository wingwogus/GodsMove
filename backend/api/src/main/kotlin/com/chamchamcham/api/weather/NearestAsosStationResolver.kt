package com.chamchamcham.api.weather

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 위경도 -> 최근접 ASOS 관측 지점 조회.
 * Haversine 거리로 전체 지점을 순회해 최소 거리 지점을 찾는 순수 함수.
 */
object NearestAsosStationResolver {
    private const val EARTH_RADIUS_KM = 6371.00877
    private const val DEGRAD = PI / 180.0

    fun resolve(latitude: Double, longitude: Double, stations: List<AsosStation> = AsosStations.ALL): AsosStation {
        return stations.minBy { haversineDistanceKm(latitude, longitude, it.latitude, it.longitude) }
    }

    private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEGRAD
        val dLon = (lon2 - lon1) * DEGRAD
        val sinHalfDLat = sin(dLat / 2)
        val sinHalfDLon = sin(dLon / 2)
        val a = sinHalfDLat * sinHalfDLat +
            cos(lat1 * DEGRAD) * cos(lat2 * DEGRAD) * sinHalfDLon * sinHalfDLon
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }
}
