package com.chamchamcham.domain.farming

enum class WorkType(val label: String, val detailRequired: Boolean) {
    PLANTING("심기", detailRequired = true),
    WATERING("관수", detailRequired = false),
    FERTILIZING("비료 주기", detailRequired = true),
    PEST_CONTROL("병해충 관리", detailRequired = true),
    WEEDING("잡초 관리", detailRequired = false),
    PRUNING("가지·순 정리", detailRequired = false),
    HARVEST("수확", detailRequired = true),
    ETC("기타", detailRequired = false),
}
