package com.chamchamcham.application.coaching.common

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizerMaterialCategory
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PesticideCategory
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType

internal fun WorkType.toCoachingText(): String = when (this) {
    WorkType.PLANTING -> "심기"
    WorkType.WATERING -> "물 주기"
    WorkType.FERTILIZING -> "거름 주기"
    WorkType.PEST_CONTROL -> "병이나 벌레 관리"
    WorkType.WEEDING -> "풀 뽑기"
    WorkType.PRUNING -> "가지 정리"
    WorkType.HARVEST -> "수확"
    WorkType.ETC -> "기타 작업"
}

internal fun PropagationMethod.toCoachingText(): String = when (this) {
    PropagationMethod.SEED -> "씨앗을 뿌림"
    PropagationMethod.CUTTING -> "가지를 잘라 심음"
    PropagationMethod.GRAFTING -> "서로 다른 줄기를 이어 붙임"
    PropagationMethod.LAYERING -> "가지를 흙에 묻어 뿌리내림"
    PropagationMethod.DIVISION -> "포기를 나눠 심음"
    PropagationMethod.TISSUE_CULTURE -> "작은 조직을 키운 모종을 심음"
}

internal fun SeedAmountUnit.toCoachingText(): String = when (this) {
    SeedAmountUnit.KG -> "킬로그램"
    SeedAmountUnit.G -> "그램"
}

internal fun SeedlingUnit.toCoachingText(): String = when (this) {
    SeedlingUnit.JU -> "포기"
}

internal fun IrrigationAmount.toCoachingText(): String = when (this) {
    IrrigationAmount.LOW -> "적은 양"
    IrrigationAmount.NORMAL -> "보통 양"
    IrrigationAmount.SUFFICIENT -> "넉넉한 양"
}

internal fun IrrigationMethod.toCoachingText(): String = when (this) {
    IrrigationMethod.DRIP -> "호스로 조금씩 물을 줌"
    IrrigationMethod.SPRINKLER -> "물을 뿌리는 장치로 줌"
    IrrigationMethod.SPRAYING -> "물을 넓게 뿌려 줌"
    IrrigationMethod.MANUAL -> "손으로 물을 줌"
}

internal fun FertilizerMaterialCategory.toCoachingText(): String = when (this) {
    FertilizerMaterialCategory.COMPOUND_FERTILIZER -> "여러 성분이 든 거름"
    FertilizerMaterialCategory.NITROGEN_FERTILIZER -> "잎과 줄기가 자라도록 돕는 거름"
    FertilizerMaterialCategory.PHOSPHATE_FERTILIZER -> "뿌리가 자라도록 돕는 거름"
    FertilizerMaterialCategory.POTASSIUM_FERTILIZER -> "작물이 튼튼하게 자라도록 돕는 거름"
    FertilizerMaterialCategory.ORGANIC_FERTILIZER -> "동식물 재료로 만든 거름"
    FertilizerMaterialCategory.LIME_FERTILIZER -> "흙의 신맛을 줄이는 거름"
    FertilizerMaterialCategory.OTHER -> "기타 거름"
}

internal fun FertilizerAmountUnit.toCoachingText(): String = when (this) {
    FertilizerAmountUnit.KG -> "킬로그램"
}

internal fun FertilizingMethod.toCoachingText(): String = when (this) {
    FertilizingMethod.SOIL -> "흙에 거름을 줌"
    FertilizingMethod.FOLIAR -> "잎에 거름물을 뿌림"
}

internal fun PesticideCategory.toCoachingText(): String = when (this) {
    PesticideCategory.FUNGICIDE -> "병을 막는 약"
    PesticideCategory.INSECTICIDE -> "벌레를 막는 약"
    PesticideCategory.HERBICIDE -> "풀을 없애는 약"
    PesticideCategory.ACARICIDE -> "응애를 막는 약"
    PesticideCategory.BIOPESTICIDE -> "생물에서 얻은 재료로 만든 약"
    PesticideCategory.OTHER -> "기타 약"
}

internal fun PesticideAmountUnit.toCoachingText(): String = when (this) {
    PesticideAmountUnit.ML -> "밀리리터"
    PesticideAmountUnit.G -> "그램"
}

internal fun SprayAmountUnit.toCoachingText(): String = when (this) {
    SprayAmountUnit.L -> "리터"
}

internal fun WeedingMethod.toCoachingText(): String = when (this) {
    WeedingMethod.HAND -> "손으로 풀을 뽑음"
    WeedingMethod.MACHINE -> "기계로 풀을 정리함"
    WeedingMethod.MULCHING -> "덮개를 깔아 풀을 막음"
    WeedingMethod.HERBICIDE -> "약으로 풀을 없앰"
}

internal fun CropUsePartCategory.toCoachingText(): String = when (this) {
    CropUsePartCategory.WHOLE_HERB -> "식물 전체"
    CropUsePartCategory.ROOT_BARK -> "뿌리와 껍질"
    CropUsePartCategory.RHIZOME -> "땅속줄기"
    CropUsePartCategory.LEAF -> "잎"
    CropUsePartCategory.FLOWER -> "꽃"
    CropUsePartCategory.FRUIT -> "열매"
    CropUsePartCategory.SEED -> "씨앗"
    CropUsePartCategory.STEM_BRANCH -> "줄기와 가지"
    CropUsePartCategory.UNKNOWN -> "기타 부위"
}

internal fun HarvestSource.toCoachingText(): String = when (this) {
    HarvestSource.CULTIVATED -> "밭에서 기름"
    HarvestSource.FORAGED -> "산이나 들에서 얻음"
}

internal fun GrowthPeriodUnit.toCoachingText(): String = when (this) {
    GrowthPeriodUnit.YEAR -> "년"
    GrowthPeriodUnit.MONTH -> "개월"
}
