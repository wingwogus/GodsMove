package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.FertilizerMaterialCategory
import com.chamchamcham.domain.farming.PesticideCategory
import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WorkTypeCatalogServiceTest {
    private lateinit var service: WorkTypeCatalogService

    @BeforeEach
    fun setUp() {
        service = WorkTypeCatalogService()
    }

    @Test
    fun `listWorkTypes returns all work types in declaration order`() {
        val result = service.listWorkTypes()

        assertThat(result.map { it.code }).containsExactlyElementsOf(WorkType.entries.map { it.name })
    }

    @Test
    fun `category enums expose stable Korean labels`() {
        assertThat(FertilizerMaterialCategory.COMPOUND_FERTILIZER.label)
            .isEqualTo("복합비료")
        assertThat(PesticideCategory.FUNGICIDE.label).isEqualTo("살균제")
    }

    @Test
    fun `category and final harvest fields expose stable contracts`() {
        val byCode = service.listWorkTypes().associateBy { it.code }

        assertThat(byCode.getValue(WorkType.FERTILIZING.name).fields.first().name)
            .isEqualTo("materialCategory")
        assertThat(byCode.getValue(WorkType.PEST_CONTROL.name).fields.first().name)
            .isEqualTo("pesticideCategory")
        assertThat(byCode.getValue(WorkType.HARVEST.name).fields.last().name)
            .isEqualTo("isFinalHarvest")
        assertThat(byCode.getValue(WorkType.HARVEST.name).fields.last().type)
            .isEqualTo(FieldValueType.BOOLEAN)
    }

    @Test
    fun `PLANTING has required detail with propagationMethod as the only required field`() {
        val planting = service.listWorkTypes().first { it.code == "PLANTING" }

        assertThat(planting.detailRequired).isTrue()
        assertThat(planting.fields.map { it.name }).containsExactly(
            "seedAmount", "seedAmountUnit", "seedlingCount", "seedlingUnit", "propagationMethod"
        )
        assertThat(planting.fields.filter { it.required }.map { it.name }).containsExactly("propagationMethod")
        val propagationMethodField = planting.fields.first { it.name == "propagationMethod" }
        assertThat(propagationMethodField.type).isEqualTo(FieldValueType.ENUM)
        assertThat(propagationMethodField.options).containsExactly(
            WorkTypeResult.EnumOptionSummary("SEED", "종자"),
            WorkTypeResult.EnumOptionSummary("CUTTING", "삽목"),
            WorkTypeResult.EnumOptionSummary("GRAFTING", "접목"),
            WorkTypeResult.EnumOptionSummary("LAYERING", "취목"),
            WorkTypeResult.EnumOptionSummary("DIVISION", "분주·분리"),
            WorkTypeResult.EnumOptionSummary("TISSUE_CULTURE", "조직배양")
        )
    }

    @Test
    fun `WATERING has optional detail with two optional enum fields`() {
        val watering = service.listWorkTypes().first { it.code == "WATERING" }

        assertThat(watering.detailRequired).isFalse()
        assertThat(watering.fields.map { it.name }).containsExactly("irrigationAmount", "irrigationMethod")
        assertThat(watering.fields).allMatch { !it.required && it.type == FieldValueType.ENUM }
    }

    @Test
    fun `FERTILIZING has required detail with three required fields`() {
        val fertilizing = service.listWorkTypes().first { it.code == "FERTILIZING" }

        assertThat(fertilizing.detailRequired).isTrue()
        assertThat(fertilizing.fields.filter { it.required }.map { it.name })
            .containsExactly("materialCategory", "amount", "amountUnit")
        assertThat(fertilizing.fields.first { it.name == "materialCategory" }.options)
            .containsExactlyElementsOf(
                FertilizerMaterialCategory.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            )
        assertThat(fertilizing.fields.first { it.name == "applicationMethod" }.required).isFalse()
    }

    @Test
    fun `PEST_CONTROL has required detail with five required fields`() {
        val pestControl = service.listWorkTypes().first { it.code == "PEST_CONTROL" }

        assertThat(pestControl.detailRequired).isTrue()
        assertThat(pestControl.fields.filter { it.required }.map { it.name }).containsExactly(
            "pesticideCategory", "pesticideAmount", "pesticideAmountUnit", "totalSprayAmount", "totalSprayAmountUnit"
        )
        assertThat(pestControl.fields.first { it.name == "pesticideCategory" }.options)
            .containsExactlyElementsOf(
                PesticideCategory.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            )
        assertThat(pestControl.fields.first { it.name == "pestTarget" }.required).isFalse()
    }

    @Test
    fun `WEEDING has optional detail with one optional enum field`() {
        val weeding = service.listWorkTypes().first { it.code == "WEEDING" }

        assertThat(weeding.detailRequired).isFalse()
        assertThat(weeding.fields.map { it.name }).containsExactly("weedingMethod")
    }

    @Test
    fun `PRUNING has no detail requirement and no fields`() {
        val pruning = service.listWorkTypes().first { it.code == "PRUNING" }

        assertThat(pruning.detailRequired).isFalse()
        assertThat(pruning.fields).isEmpty()
    }

    @Test
    fun `ETC has no detail requirement and no fields`() {
        val etc = service.listWorkTypes().first { it.code == "ETC" }

        assertThat(etc.detailRequired).isFalse()
        assertThat(etc.fields).isEmpty()
    }

    @Test
    fun `HARVEST has required detail including medicinalPart`() {
        val harvest = service.listWorkTypes().first { it.code == "HARVEST" }

        assertThat(harvest.detailRequired).isTrue()
        assertThat(harvest.fields.map { it.name }).containsExactly(
            "harvestAmount", "medicinalPart", "harvestSource", "growthPeriod", "growthPeriodUnit", "isFinalHarvest"
        )
        assertThat(harvest.fields.first { it.name == "medicinalPart" }.required).isTrue()
        assertThat(harvest.fields.first { it.name == "harvestSource" }.required).isFalse()
        assertThat(harvest.fields.first { it.name == "isFinalHarvest" }.required).isTrue()
        assertThat(harvest.fields.first { it.name == "isFinalHarvest" }.type).isEqualTo(FieldValueType.BOOLEAN)
    }

    @Test
    fun `detailRequired matches the requirement enforced by FarmingRecordDetailValidator`() {
        val byCode = service.listWorkTypes().associateBy { it.code }

        assertThat(byCode.getValue("FERTILIZING").detailRequired).isTrue()
        assertThat(byCode.getValue("PEST_CONTROL").detailRequired).isTrue()
        assertThat(byCode.getValue("HARVEST").detailRequired).isTrue()
        assertThat(byCode.getValue("PLANTING").detailRequired).isTrue()
        assertThat(byCode.getValue("WATERING").detailRequired).isFalse()
        assertThat(byCode.getValue("WEEDING").detailRequired).isFalse()
        assertThat(byCode.getValue("PRUNING").detailRequired).isFalse()
        assertThat(byCode.getValue("ETC").detailRequired).isFalse()
    }
}
