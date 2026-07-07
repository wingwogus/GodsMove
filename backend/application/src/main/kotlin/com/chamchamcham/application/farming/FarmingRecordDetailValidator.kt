package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farming.SeedSource
import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Component

interface FarmingRecordDetailValidator {
    fun validate(command: FarmingRecordCommand.Create)
}

@Component
class DefaultFarmingRecordDetailValidator : FarmingRecordDetailValidator {
    override fun validate(command: FarmingRecordCommand.Create) {
        when (command.workType) {
            WorkType.PLANTING -> {
                val detail = command.planting
                if (detail?.seedSource == SeedSource.PURCHASED && detail.seedPurchasePlace.isNullOrBlank()) {
                    throw BusinessException(ErrorCode.FARMING_RECORD_INVALID_DETAIL)
                }
            }
            WorkType.FERTILIZING -> command.fertilizing ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            WorkType.PEST_CONTROL -> command.pestControl ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            WorkType.HARVEST -> command.harvest ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            WorkType.WATERING, WorkType.WEEDING, WorkType.PRUNING -> Unit
        }
    }
}
