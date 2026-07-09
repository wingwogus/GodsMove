package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farming.SeedSource
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
                val detail = payload.planting
                if (detail?.seedSource == SeedSource.PURCHASED && detail.seedPurchasePlace.isNullOrBlank()) {
                    throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
                }
            }
            WorkType.FERTILIZING -> requireDetail(payload.workType, payload.fertilizing)
            WorkType.PEST_CONTROL -> requireDetail(payload.workType, payload.pestControl)
            WorkType.HARVEST -> requireDetail(payload.workType, payload.harvest)
            WorkType.WATERING, WorkType.WEEDING, WorkType.PRUNING -> Unit
        }
    }

    private fun requireDetail(workType: WorkType, detail: Any?) {
        check(workType.detailRequired) { "WorkType $workType is not marked as detailRequired" }
        detail ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
    }
}
