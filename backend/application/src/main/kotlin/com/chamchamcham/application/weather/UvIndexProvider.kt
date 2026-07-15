package com.chamchamcham.application.weather

fun interface UvIndexProvider {
    fun fetchUvIndex(areaNo: String): Int?
}
