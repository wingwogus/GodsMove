package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Component

interface FarmingRecordDetailValidator {
    fun validate(payload: FarmingRecordDetailPayload)
}

@Component
class DefaultFarmingRecordDetailValidator : FarmingRecordDetailValidator {
    override fun validate(payload: FarmingRecordDetailPayload) {
        when (payload.workType) {
            WorkType.PLANTING -> {
                requireDetail(payload.workType, payload.planting)
                validatePlanting(payload.planting)
            }
            WorkType.FERTILIZING -> requireDetail(payload.workType, payload.fertilizing)
            WorkType.PEST_CONTROL -> requireDetail(payload.workType, payload.pestControl)
            WorkType.HARVEST -> {
                requireDetail(payload.workType, payload.harvest)
                validateHarvest(payload.harvest)
            }
            WorkType.WATERING, WorkType.WEEDING, WorkType.PRUNING, WorkType.ETC -> Unit
        }
    }

    private fun requireDetail(workType: WorkType, detail: Any?) {
        check(workType.detailRequired) { "WorkType $workType is not marked as detailRequired" }
        detail ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
    }

    // plantingMethod=SEED면 seedAmount는 필수이고 seedlingCount/seedlingUnit/propagationMethod는
    // 모두 비워야 한다. plantingMethod=SEEDLING이면 seedlingCount는 필수, propagationMethod는
    // 선택이며 seedAmount/seedAmountUnit은 비워야 한다.
    private fun validatePlanting(detail: FarmingRecordCommand.PlantingDetail?) {
        detail ?: return
        when (detail.plantingMethod) {
            PlantingMethod.SEED -> {
                if (detail.seedAmount == null) {
                    throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
                }
                if (detail.seedlingCount != null || detail.seedlingUnit != null || detail.propagationMethod != null) {
                    throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
                }
            }

            PlantingMethod.SEEDLING -> {
                if (detail.seedlingCount == null) {
                    throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
                }
                if (detail.seedAmount != null || detail.seedAmountUnit != null) {
                    throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
                }
            }
        }
    }

    // 수확량은 kg로 입력하거나, 모르면 amountUnknown=true로 비워둔다. 둘 다 없거나(누락) 둘 다
    // 있으면(모순) 거부한다. 모름은 항상 NULL로 저장되어 0과 구분된다.
    // 수확 부위(medicinalPart)는 선택이다. 재배기간(growthPeriod)은 API 경계에서 필수로 강제된다.
    private fun validateHarvest(detail: FarmingRecordCommand.HarvestDetail?) {
        detail ?: return
        if (!detail.amountUnknown && detail.harvestAmount == null) {
            throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
        }
        if (detail.amountUnknown && detail.harvestAmount != null) {
            throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
        }
    }
}
