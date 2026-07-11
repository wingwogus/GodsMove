package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farming.PropagationMethod
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

    // 번식법이 종자(SEED)면 파종 항목(seedAmount/seedAmountUnit)만, 그 외 번식법이면 정식 항목
    // (seedlingCount/seedlingUnit)만 입력을 허용한다. 반대쪽 그룹이 채워져 있으면 거부한다.
    private fun validatePlanting(detail: FarmingRecordCommand.PlantingDetail?) {
        detail ?: return
        val seedGroupFilled = detail.seedAmount != null || detail.seedAmountUnit != null
        val seedlingGroupFilled = detail.seedlingCount != null || detail.seedlingUnit != null

        if (detail.propagationMethod == PropagationMethod.SEED) {
            if (seedlingGroupFilled) {
                throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
            }
        } else if (seedGroupFilled) {
            throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
        }
    }

    // 수확량은 kg로 입력하거나, 모르면 amountUnknown=true로 비워둔다. 둘 다 없거나(누락) 둘 다
    // 있으면(모순) 거부한다. 모름은 항상 NULL로 저장되어 0과 구분된다.
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
