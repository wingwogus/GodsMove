package com.chamchamcham.domain.farming

enum class WorkType(val label: String, val detailRequired: Boolean) {
    PLANTING("파종/정식", detailRequired = false),
    WATERING("관수", detailRequired = false),
    FERTILIZING("시비", detailRequired = true),
    PEST_CONTROL("병해충 방제", detailRequired = true),
    WEEDING("제초", detailRequired = false),
    PRUNING("전정", detailRequired = false),
    HARVEST("수확", detailRequired = true),
}
