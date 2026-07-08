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
            WorkType.FERTILIZING -> payload.fertilizing ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            WorkType.PEST_CONTROL -> payload.pestControl ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            WorkType.HARVEST -> payload.harvest ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            WorkType.WATERING, WorkType.WEEDING, WorkType.PRUNING -> Unit
        }
    }
}
