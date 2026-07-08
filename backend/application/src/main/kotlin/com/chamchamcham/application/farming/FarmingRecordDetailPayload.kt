package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.WorkType

interface FarmingRecordDetailPayload {
    val workType: WorkType
    val planting: FarmingRecordCommand.PlantingDetail?
    val watering: FarmingRecordCommand.WateringDetail?
    val fertilizing: FarmingRecordCommand.FertilizingDetail?
    val pestControl: FarmingRecordCommand.PestControlDetail?
    val weeding: FarmingRecordCommand.WeedingDetail?
    val harvest: FarmingRecordCommand.HarvestDetail?
}
