package com.chamchamcham.application.pesticide.sync

data class PsisPesticideRow(
    val itemName: String,
    val brandName: String,
    val cropName: String,
    val pestName: String,
    val activeIngredient: String?,
    val formulation: String?,
    val usageCategory: String?,
    val humanToxicity: String?,
    val fishToxicity: String?,
    val manufacturer: String?,
    val dilutionRate: String?,
    val usageAmount: String?,
    val usageTiming: String?,
    val maxUsageCount: String?,
)
