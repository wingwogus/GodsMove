package com.chamchamcham.domain.farming

enum class PesticideCategory(val label: String) {
    FUNGICIDE("살균제"),
    INSECTICIDE("살충제"),
    HERBICIDE("제초제"),
    ACARICIDE("살비제"),
    BIOPESTICIDE("생물농약"),
    OTHER("기타"),
}
