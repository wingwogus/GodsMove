package com.chamchamcham.domain.farming

enum class FertilizerMaterialCategory(val label: String) {
    COMPOUND_FERTILIZER("복합비료"),
    NITROGEN_FERTILIZER("질소질비료"),
    PHOSPHATE_FERTILIZER("인산질비료"),
    POTASSIUM_FERTILIZER("칼리질비료"),
    ORGANIC_FERTILIZER("유기질비료"),
    LIME_FERTILIZER("석회질비료"),
    OTHER("기타"),
}
