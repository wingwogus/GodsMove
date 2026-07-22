package com.chamchamcham.application.pesticide.sync

import org.springframework.stereotype.Component

/**
 * PSIS(농약안전정보시스템) 실응답의 실제 태그명으로 raw item 맵을 [PsisPesticideRow]로 매핑한다.
 * formulation/fishToxicity/usageAmount는 PSIS 응답에 대응하는 태그가 없어 항상 null이다.
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
            formulation = null,
            usageCategory = raw.firstNotBlank(USAGE_CATEGORY_KEYS),
            humanToxicity = raw.firstNotBlank(HUMAN_TOXICITY_KEYS),
            fishToxicity = null,
            manufacturer = raw.firstNotBlank(MANUFACTURER_KEYS),
            dilutionRate = raw.firstNotBlank(DILUTION_RATE_KEYS),
            usageAmount = null,
            usageTiming = raw.firstNotBlank(USAGE_TIMING_KEYS),
            maxUsageCount = raw.firstNotBlank(MAX_USAGE_COUNT_KEYS),
        )
    }

    fun diagnoseRequired(raw: Map<String, String>): Map<String, Boolean> = mapOf(
        "itemName" to (raw.firstNotBlank(ITEM_NAME_KEYS) != null),
        "cropName" to (raw.firstNotBlank(CROP_NAME_KEYS) != null),
        "pestName" to (raw.firstNotBlank(PEST_NAME_KEYS) != null),
    )

    private fun Map<String, String>.firstNotBlank(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { this[it]?.trim()?.takeIf(String::isNotEmpty) }

    private companion object {
        val ITEM_NAME_KEYS = listOf("pestiKorName")
        val BRAND_NAME_KEYS = listOf("pestiBrandName")
        val CROP_NAME_KEYS = listOf("cropName")
        val PEST_NAME_KEYS = listOf("diseaseWeedName")
        val ACTIVE_INGREDIENT_KEYS = listOf("engName")
        val USAGE_CATEGORY_KEYS = listOf("useName")
        val HUMAN_TOXICITY_KEYS = listOf("indictSymbl")
        val MANUFACTURER_KEYS = listOf("compName")
        val DILUTION_RATE_KEYS = listOf("dilutUnit")
        val USAGE_TIMING_KEYS = listOf("useSuittime")
        val MAX_USAGE_COUNT_KEYS = listOf("useNum")
    }
}
