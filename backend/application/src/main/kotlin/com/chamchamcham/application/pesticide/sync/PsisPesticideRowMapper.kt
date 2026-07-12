package com.chamchamcham.application.pesticide.sync

import org.springframework.stereotype.Component

/**
 * PSIS 원본 응답의 태그명이 아직 확정되지 않아, 필드마다 그럴듯한 후보 태그명을 여러 개 시도한다.
 * API 키 발급 후 실응답을 받으면 각 필드의 실제 태그명만 후보 목록 맨 앞에 추가하면 된다
 * (구조 변경 불필요 — 이 클래스만 수정).
 */
@Component
class PsisPesticideRowMapper {
    fun map(raw: Map<String, String>): PsisPesticideRow? {
        val itemName = raw.firstNotBlank(ITEM_NAME_KEYS) ?: return null
        val brandName = raw.firstNotBlank(BRAND_NAME_KEYS) ?: itemName
        val cropName = raw.firstNotBlank(CROP_NAME_KEYS) ?: return null
        val pestName = raw.firstNotBlank(PEST_NAME_KEYS) ?: return null

        return PsisPesticideRow(
            itemName = itemName,
            brandName = brandName,
            cropName = cropName,
            pestName = pestName,
            activeIngredient = raw.firstNotBlank(ACTIVE_INGREDIENT_KEYS),
            formulation = raw.firstNotBlank(FORMULATION_KEYS),
            usageCategory = raw.firstNotBlank(USAGE_CATEGORY_KEYS),
            humanToxicity = raw.firstNotBlank(HUMAN_TOXICITY_KEYS),
            fishToxicity = raw.firstNotBlank(FISH_TOXICITY_KEYS),
            manufacturer = raw.firstNotBlank(MANUFACTURER_KEYS),
            dilutionRate = raw.firstNotBlank(DILUTION_RATE_KEYS),
            usageAmount = raw.firstNotBlank(USAGE_AMOUNT_KEYS),
            usageTiming = raw.firstNotBlank(USAGE_TIMING_KEYS),
            maxUsageCount = raw.firstNotBlank(MAX_USAGE_COUNT_KEYS),
        )
    }

    private fun Map<String, String>.firstNotBlank(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { this[it]?.trim()?.takeIf(String::isNotEmpty) }

    private companion object {
        val ITEM_NAME_KEYS = listOf("prdtNm", "pestcdNm", "itemNm")
        val BRAND_NAME_KEYS = listOf("trdmrkNm", "brandNm", "cmpnyNm2")
        val CROP_NAME_KEYS = listOf("cropNm", "aplyCropNm", "cropName")
        val PEST_NAME_KEYS = listOf("aplyPestNm", "pestNm", "diszInsctNm", "pestName")
        val ACTIVE_INGREDIENT_KEYS = listOf("mkeqCn", "ftlDstncNm", "activeIngredient")
        val FORMULATION_KEYS = listOf("frmlcNm", "formulation")
        val USAGE_CATEGORY_KEYS = listOf("useNm", "purpsNm", "usageCategory")
        val HUMAN_TOXICITY_KEYS = listOf("humanToxNm", "hyginTxctyNm")
        val FISH_TOXICITY_KEYS = listOf("fishToxNm", "fshTxctyNm")
        val MANUFACTURER_KEYS = listOf("cmpnyNm", "manufacturer")
        val DILUTION_RATE_KEYS = listOf("dltnMag", "dilutionRate")
        val USAGE_AMOUNT_KEYS = listOf("useAmount", "useQnty")
        val USAGE_TIMING_KEYS = listOf("useTiming", "useTermNm")
        val MAX_USAGE_COUNT_KEYS = listOf("useNmtm", "useMaxCnt")
    }
}
