package com.chamchamcham.api.weather

/**
 * 종관기상관측(ASOS) 지점 정보.
 * @param id 지점 번호(stnId)
 * @param name 지점명
 * @param latitude 관측지점 위도
 * @param longitude 관측지점 경도
 */
data class AsosStation(val id: String, val name: String, val latitude: Double, val longitude: Double)
