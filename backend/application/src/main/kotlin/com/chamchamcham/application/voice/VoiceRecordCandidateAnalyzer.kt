package com.chamchamcham.application.voice

import com.chamchamcham.domain.farming.WorkType

/**
 * BR-RECORD-003(필수값: worked_at/farm/crop/work_type)와 WorkType.detailRequired를
 * 후보값에 대해 그대로 적용해 누락 필드를 계산한다. FarmingRecordDetailValidator는 저장 시점에
 * 예외를 던지는 반면, 여긴 확인(WAITING_CONFIRMATION) 화면에 보여줄 누락 목록만 계산한다.
 */
object VoiceRecordCandidateAnalyzer {
    fun missingFields(candidate: VoiceRecordCandidate): List<String> {
        val missing = mutableListOf<String>()
        if (candidate.farmId == null) missing += "farmId"
        if (candidate.cropId == null) missing += "cropId"
        if (candidate.workType == null) missing += "workType"
        if (candidate.workedAt == null) missing += "workedAt"

        val workType = candidate.workType
        if (workType != null && workType.detailRequired && detailOf(workType, candidate) == null) {
            missing += "detail"
        }

        return missing
    }

    private fun detailOf(workType: WorkType, candidate: VoiceRecordCandidate): Any? = when (workType) {
        WorkType.FERTILIZING -> candidate.fertilizing
        WorkType.PEST_CONTROL -> candidate.pestControl
        WorkType.HARVEST -> candidate.harvest
        WorkType.PLANTING, WorkType.WATERING, WorkType.WEEDING, WorkType.PRUNING -> Unit
    }
}
