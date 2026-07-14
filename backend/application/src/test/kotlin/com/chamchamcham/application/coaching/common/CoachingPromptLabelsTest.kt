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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingPromptLabelsTest {
    @Test
    fun `maps every work type to an easy coaching label`() {
        assertThat(WorkType.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                WorkType.PLANTING to "심기",
                WorkType.WATERING to "물 주기",
                WorkType.FERTILIZING to "거름 주기",
                WorkType.PEST_CONTROL to "병이나 벌레 관리",
                WorkType.WEEDING to "풀 뽑기",
                WorkType.PRUNING to "가지 정리",
                WorkType.HARVEST to "수확",
                WorkType.ETC to "기타 작업",
            ),
        )
    }

    @Test
    fun `maps every planting value to an easy coaching label`() {
        assertThat(PropagationMethod.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                PropagationMethod.SEED to "씨앗을 뿌림",
                PropagationMethod.CUTTING to "가지를 잘라 심음",
                PropagationMethod.GRAFTING to "서로 다른 줄기를 이어 붙임",
                PropagationMethod.LAYERING to "가지를 흙에 묻어 뿌리내림",
                PropagationMethod.DIVISION to "포기를 나눠 심음",
                PropagationMethod.TISSUE_CULTURE to "작은 조직을 키운 모종을 심음",
            ),
        )
        assertThat(SeedAmountUnit.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(SeedAmountUnit.KG to "킬로그램", SeedAmountUnit.G to "그램"),
        )
        assertThat(SeedlingUnit.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(SeedlingUnit.JU to "포기"),
        )
    }

    @Test
    fun `maps every watering value to an easy coaching label`() {
        assertThat(IrrigationAmount.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                IrrigationAmount.LOW to "적은 양",
                IrrigationAmount.NORMAL to "보통 양",
                IrrigationAmount.SUFFICIENT to "넉넉한 양",
            ),
        )
        assertThat(IrrigationMethod.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                IrrigationMethod.DRIP to "호스로 조금씩 물을 줌",
                IrrigationMethod.SPRINKLER to "물을 뿌리는 장치로 줌",
                IrrigationMethod.SPRAYING to "물을 넓게 뿌려 줌",
                IrrigationMethod.MANUAL to "손으로 물을 줌",
            ),
        )
    }

    @Test
    fun `maps every fertilizing value to an easy coaching label`() {
        assertThat(FertilizerMaterialCategory.entries.associateWith { it.toCoachingText() })
            .containsExactlyEntriesOf(
                mapOf(
                    FertilizerMaterialCategory.COMPOUND_FERTILIZER to "여러 성분이 든 거름",
                    FertilizerMaterialCategory.NITROGEN_FERTILIZER to "잎과 줄기가 자라도록 돕는 거름",
                    FertilizerMaterialCategory.PHOSPHATE_FERTILIZER to "뿌리가 자라도록 돕는 거름",
                    FertilizerMaterialCategory.POTASSIUM_FERTILIZER to "작물이 튼튼하게 자라도록 돕는 거름",
                    FertilizerMaterialCategory.ORGANIC_FERTILIZER to "동식물 재료로 만든 거름",
                    FertilizerMaterialCategory.LIME_FERTILIZER to "흙의 신맛을 줄이는 거름",
                    FertilizerMaterialCategory.OTHER to "기타 거름",
                ),
            )
        assertThat(FertilizingMethod.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                FertilizingMethod.SOIL to "흙에 거름을 줌",
                FertilizingMethod.FOLIAR to "잎에 거름물을 뿌림",
            ),
        )
        assertThat(FertilizerAmountUnit.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(FertilizerAmountUnit.KG to "킬로그램"),
        )
    }

    @Test
    fun `maps every pest control value to an easy coaching label`() {
        assertThat(PesticideCategory.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                PesticideCategory.FUNGICIDE to "병을 막는 약",
                PesticideCategory.INSECTICIDE to "벌레를 막는 약",
                PesticideCategory.HERBICIDE to "풀을 없애는 약",
                PesticideCategory.ACARICIDE to "응애를 막는 약",
                PesticideCategory.BIOPESTICIDE to "생물에서 얻은 재료로 만든 약",
                PesticideCategory.OTHER to "기타 약",
            ),
        )
        assertThat(PesticideAmountUnit.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(PesticideAmountUnit.ML to "밀리리터", PesticideAmountUnit.G to "그램"),
        )
        assertThat(SprayAmountUnit.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(SprayAmountUnit.L to "리터"),
        )
    }

    @Test
    fun `maps every weeding and harvest value to an easy coaching label`() {
        assertThat(WeedingMethod.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                WeedingMethod.HAND to "손으로 풀을 뽑음",
                WeedingMethod.MACHINE to "기계로 풀을 정리함",
                WeedingMethod.MULCHING to "덮개를 깔아 풀을 막음",
                WeedingMethod.HERBICIDE to "약으로 풀을 없앰",
            ),
        )
        assertThat(CropUsePartCategory.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(
                CropUsePartCategory.WHOLE_HERB to "식물 전체",
                CropUsePartCategory.ROOT_BARK to "뿌리와 껍질",
                CropUsePartCategory.RHIZOME to "땅속줄기",
                CropUsePartCategory.LEAF to "잎",
                CropUsePartCategory.FLOWER to "꽃",
                CropUsePartCategory.FRUIT to "열매",
                CropUsePartCategory.SEED to "씨앗",
                CropUsePartCategory.STEM_BRANCH to "줄기와 가지",
                CropUsePartCategory.UNKNOWN to "기타 부위",
            ),
        )
        assertThat(HarvestSource.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(HarvestSource.CULTIVATED to "밭에서 기름", HarvestSource.FORAGED to "산이나 들에서 얻음"),
        )
        assertThat(GrowthPeriodUnit.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
            mapOf(GrowthPeriodUnit.YEAR to "년", GrowthPeriodUnit.MONTH to "개월"),
        )
    }

    @Test
    fun `coaching labels never expose raw enum names or prohibited language`() {
        val labels = buildList {
            addAll(WorkType.entries.map { it.toCoachingText() })
            addAll(PropagationMethod.entries.map { it.toCoachingText() })
            addAll(IrrigationAmount.entries.map { it.toCoachingText() })
            addAll(IrrigationMethod.entries.map { it.toCoachingText() })
            addAll(FertilizerMaterialCategory.entries.map { it.toCoachingText() })
            addAll(FertilizingMethod.entries.map { it.toCoachingText() })
            addAll(PesticideCategory.entries.map { it.toCoachingText() })
            addAll(WeedingMethod.entries.map { it.toCoachingText() })
            addAll(CropUsePartCategory.entries.map { it.toCoachingText() })
            addAll(HarvestSource.entries.map { it.toCoachingText() })
        }

        assertThat(labels).allMatch { !CoachingTextPolicy.hasDisallowedLanguage(it) }
        WorkType.entries.forEach { value ->
            assertThat(value.toCoachingText()).isNotEqualTo(value.name)
        }
    }
}
