package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestAmountUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedSource
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType

// 손으로 작성한 테이블로, api.farming.dto.FarmingRecordRequests의 *DetailRequest DTO들을 필드 단위로 그대로 옮겨적은 것이다.
// 해당 DTO/엔티티가 바뀌면 이 파일도 함께 수동으로 맞춰야 한다.
object WorkTypeFieldCatalog {
    private val FIELDS: Map<WorkType, List<WorkTypeResult.FieldSummary>> = mapOf(
        WorkType.PLANTING to listOf(
            WorkTypeResult.FieldSummary(
                name = "seedAmount",
                type = FieldValueType.DECIMAL,
                required = false,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "seedAmountUnit",
                type = FieldValueType.ENUM,
                required = false,
                options = SeedAmountUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "seedlingCount",
                type = FieldValueType.INT,
                required = false,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "seedlingUnit",
                type = FieldValueType.ENUM,
                required = false,
                options = SeedlingUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "seedSource",
                type = FieldValueType.ENUM,
                required = false,
                options = SeedSource.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "seedPurchasePlace",
                type = FieldValueType.STRING,
                required = false,
                options = emptyList()
            )
        ),
        WorkType.WATERING to listOf(
            WorkTypeResult.FieldSummary(
                name = "irrigationAmount",
                type = FieldValueType.ENUM,
                required = false,
                options = IrrigationAmount.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "irrigationMethod",
                type = FieldValueType.ENUM,
                required = false,
                options = IrrigationMethod.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            )
        ),
        WorkType.FERTILIZING to listOf(
            WorkTypeResult.FieldSummary(
                name = "materialName",
                type = FieldValueType.STRING,
                required = true,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "amount",
                type = FieldValueType.DECIMAL,
                required = true,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "amountUnit",
                type = FieldValueType.ENUM,
                required = true,
                options = FertilizerAmountUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "applicationMethod",
                type = FieldValueType.ENUM,
                required = false,
                options = FertilizingMethod.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            )
        ),
        WorkType.PEST_CONTROL to listOf(
            WorkTypeResult.FieldSummary(
                name = "pesticideName",
                type = FieldValueType.STRING,
                required = true,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "pesticideAmount",
                type = FieldValueType.DECIMAL,
                required = true,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "pesticideAmountUnit",
                type = FieldValueType.ENUM,
                required = true,
                options = PesticideAmountUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "totalSprayAmount",
                type = FieldValueType.DECIMAL,
                required = true,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "totalSprayAmountUnit",
                type = FieldValueType.ENUM,
                required = true,
                options = SprayAmountUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "pestTarget",
                type = FieldValueType.STRING,
                required = false,
                options = emptyList()
            )
        ),
        WorkType.WEEDING to listOf(
            WorkTypeResult.FieldSummary(
                name = "weedingMethod",
                type = FieldValueType.ENUM,
                required = false,
                options = WeedingMethod.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            )
        ),
        WorkType.PRUNING to emptyList(),
        WorkType.HARVEST to listOf(
            // medicinalPart는 의도적으로 제외: 기록 자체 입력값이 아니라 연결된 Crop의 CropUsePartCategory에서 파생됨
            WorkTypeResult.FieldSummary(
                name = "harvestAmount",
                type = FieldValueType.DECIMAL,
                required = true,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "harvestAmountUnit",
                type = FieldValueType.ENUM,
                required = true,
                options = HarvestAmountUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "harvestSource",
                type = FieldValueType.ENUM,
                required = false,
                options = HarvestSource.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            ),
            WorkTypeResult.FieldSummary(
                name = "growthPeriod",
                type = FieldValueType.INT,
                required = true,
                options = emptyList()
            ),
            WorkTypeResult.FieldSummary(
                name = "growthPeriodUnit",
                type = FieldValueType.ENUM,
                required = true,
                options = GrowthPeriodUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
            )
        )
    )

    fun fieldsFor(workType: WorkType): List<WorkTypeResult.FieldSummary> = FIELDS.getValue(workType)
}
