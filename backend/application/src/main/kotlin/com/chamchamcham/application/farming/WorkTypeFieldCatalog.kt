package com.chamchamcham.application.farming

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType

// 손으로 작성한 테이블로, api.farming.dto.FarmingRecordRequests의 *DetailRequest DTO들을 필드 단위로 그대로 옮겨적은 것이다.
// 해당 DTO/엔티티가 바뀌면 이 파일도 함께 수동으로 맞춰야 한다.
object WorkTypeFieldCatalog {
    fun fieldsFor(workType: WorkType): List<WorkTypeResult.FieldSummary> = when (workType) {
        WorkType.PLANTING -> PLANTING_FIELDS
        WorkType.WATERING -> WATERING_FIELDS
        WorkType.FERTILIZING -> FERTILIZING_FIELDS
        WorkType.PEST_CONTROL -> PEST_CONTROL_FIELDS
        WorkType.WEEDING -> WEEDING_FIELDS
        WorkType.PRUNING -> emptyList()
        WorkType.HARVEST -> HARVEST_FIELDS
        WorkType.ETC -> emptyList()
    }

    // plantingMethod=SEED면 seedAmount/seedAmountUnit만, plantingMethod=SEEDLING이면
    // seedlingCount/seedlingUnit(+선택적으로 propagationMethod)만 입력 가능하다
    // (FarmingRecordDetailValidator.validatePlanting에서 강제). 이 목록 자체는 조건을 표현하지
    // 않으므로 프론트는 검증 규칙을 별도로 반영해야 한다.
    private val PLANTING_FIELDS: List<WorkTypeResult.FieldSummary> = listOf(
        WorkTypeResult.FieldSummary(
            name = "plantingMethod",
            type = FieldValueType.ENUM,
            required = true,
            options = PlantingMethod.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
        ),
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
            name = "propagationMethod",
            type = FieldValueType.ENUM,
            required = false,
            options = PropagationMethod.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
        )
    )

    private val WATERING_FIELDS: List<WorkTypeResult.FieldSummary> = listOf(
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
    )

    private val FERTILIZING_FIELDS: List<WorkTypeResult.FieldSummary> = listOf(
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
    )

    private val PEST_CONTROL_FIELDS: List<WorkTypeResult.FieldSummary> = listOf(
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
    )

    private val WEEDING_FIELDS: List<WorkTypeResult.FieldSummary> = listOf(
        WorkTypeResult.FieldSummary(
            name = "weedingMethod",
            type = FieldValueType.ENUM,
            required = false,
            options = WeedingMethod.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
        )
    )

    // harvestAmount는 kg 단위로 필수이나, 사용자가 '모르겠음'을 선택하면(harvestAmountUnknown=true)
    // 비워둘 수 있다(FarmingRecordDetailValidator.validateHarvest에서 강제). 이 경우 서버는 NULL로
    // 저장한다(0 아님). medicinalPart는 선택이다(온보딩 기본값으로 프론트가 미리 채워둔다).
    // growthPeriod/growthPeriodUnit은 둘 다 비거나 둘 다 채워져야 한다(validateHarvest에서 강제).
    // 이 목록 자체는 그 예외들을 표현하지 않으므로 프론트가 별도 처리해야 한다.
    private val HARVEST_FIELDS: List<WorkTypeResult.FieldSummary> = listOf(
        WorkTypeResult.FieldSummary(
            name = "harvestAmount",
            type = FieldValueType.DECIMAL,
            required = true,
            options = emptyList()
        ),
        WorkTypeResult.FieldSummary(
            name = "medicinalPart",
            type = FieldValueType.ENUM,
            required = false,
            options = CropUsePartCategory.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
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
            required = false,
            options = emptyList()
        ),
        WorkTypeResult.FieldSummary(
            name = "growthPeriodUnit",
            type = FieldValueType.ENUM,
            required = false,
            options = GrowthPeriodUnit.entries.map { WorkTypeResult.EnumOptionSummary(it.name, it.label) }
        )
    )
}
