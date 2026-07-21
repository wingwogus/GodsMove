package com.chamchamcham.application.weather

import com.chamchamcham.domain.farming.WorkType

data class FarmingAdvice(
    val workType: WorkType,
    val level: AdviceLevel,
    val message: String
)
