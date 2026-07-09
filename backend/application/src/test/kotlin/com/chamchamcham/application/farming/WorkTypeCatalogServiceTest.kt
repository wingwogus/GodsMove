package com.chamchamcham.application.farming

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
    fun `PLANTING has optional detail with six optional fields`() {
        val planting = service.listWorkTypes().first { it.code == "PLANTING" }

        assertThat(planting.detailRequired).isFalse()
        assertThat(planting.fields.map { it.name }).containsExactly(
            "seedAmount", "seedAmountUnit", "seedlingCount", "seedlingUnit", "seedSource", "seedPurchasePlace"
        )
        assertThat(planting.fields).allMatch { !it.required }
        val seedSourceField = planting.fields.first { it.name == "seedSource" }
        assertThat(seedSourceField.type).isEqualTo(FieldValueType.ENUM)
        assertThat(seedSourceField.options).containsExactly(
            WorkTypeResult.EnumOptionSummary("SELF_COLLECTED", "자가채종"),
            WorkTypeResult.EnumOptionSummary("PURCHASED", "구매")
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
            .containsExactly("materialName", "amount", "amountUnit")
        assertThat(fertilizing.fields.first { it.name == "applicationMethod" }.required).isFalse()
    }

    @Test
    fun `PEST_CONTROL has required detail with five required fields`() {
        val pestControl = service.listWorkTypes().first { it.code == "PEST_CONTROL" }

        assertThat(pestControl.detailRequired).isTrue()
        assertThat(pestControl.fields.filter { it.required }.map { it.name }).containsExactly(
            "pesticideName", "pesticideAmount", "pesticideAmountUnit", "totalSprayAmount", "totalSprayAmountUnit"
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
    fun `HARVEST has required detail and does not include medicinalPart`() {
        val harvest = service.listWorkTypes().first { it.code == "HARVEST" }

        assertThat(harvest.detailRequired).isTrue()
        assertThat(harvest.fields.map { it.name }).doesNotContain("medicinalPart")
        assertThat(harvest.fields.map { it.name }).containsExactly(
            "harvestAmount", "harvestAmountUnit", "harvestSource", "growthPeriod", "growthPeriodUnit"
        )
        assertThat(harvest.fields.first { it.name == "harvestSource" }.required).isFalse()
    }

    @Test
    fun `detailRequired matches the requirement enforced by FarmingRecordDetailValidator`() {
        val byCode = service.listWorkTypes().associateBy { it.code }

        assertThat(byCode.getValue("FERTILIZING").detailRequired).isTrue()
        assertThat(byCode.getValue("PEST_CONTROL").detailRequired).isTrue()
        assertThat(byCode.getValue("HARVEST").detailRequired).isTrue()
        assertThat(byCode.getValue("PLANTING").detailRequired).isFalse()
        assertThat(byCode.getValue("WATERING").detailRequired).isFalse()
        assertThat(byCode.getValue("WEEDING").detailRequired).isFalse()
        assertThat(byCode.getValue("PRUNING").detailRequired).isFalse()
    }
}
