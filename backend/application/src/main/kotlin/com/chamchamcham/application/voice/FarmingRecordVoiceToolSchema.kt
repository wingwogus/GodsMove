package com.chamchamcham.application.voice

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

/**
 * OpenAI Realtime 세션에 등록하는 save_farming_record 도구(tool) 정의를 만든다.
 * FarmingRecordCommand.Create / FarmingRecordRequests.SaveRecordRequest에 정의된 필드와
 * FarmingRecordDetailValidator의 업무 규칙을 그대로 반영한다. 두 파일과 이 파일이 어긋나면
 * 음성으로 추출한 값이 실제 저장 시점(확인/승인 API)에 거부될 수 있으므로 함께 갱신해야 한다.
 *
 * farmId/cropId는 이 회원이 실제로 소유/등록한 값으로만 enum 제한해, 모델이 다른 회원의
 * 농지/작물 ID를 생성하거나 존재하지 않는 값을 만들어낼 수 없게 한다.
 */
object FarmingRecordVoiceToolSchema {
    fun build(farms: List<FarmOption>, cropsByFarm: Map<String, List<CropOption>>): Map<String, Any?> {
        val cropOptions = cropsByFarm.values.flatten().distinctBy { it.cropId }

        return mapOf(
            "type" to "function",
            "name" to "save_farming_record",
            "description" to
                "사용자가 말한 영농 작업 내용을 영농일지 항목으로 구조화한다. " +
                "누락된 필수 항목이 있으면 이 도구를 호출하지 말고 사용자에게 먼저 되물어라.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "farmId" to enumProperty("이 기록이 속한 농지 ID", farms.map { it.farmId.toString() }),
                    "cropId" to enumProperty("이 기록이 속한 작물 ID", cropOptions.map { it.cropId.toString() }),
                    "workType" to enumProperty("작업 유형", WorkType.entries.map { it.name }),
                    "workedAt" to mapOf(
                        "type" to "string",
                        "format" to "date-time",
                        "description" to "작업 일시(ISO-8601). 사용자가 상대 시간을 언급하지 않으면 생략한다.",
                    ),
                    "memo" to mapOf("type" to "string", "description" to "작업 내용 메모"),
                    "planting" to plantingSchema(),
                    "watering" to wateringSchema(),
                    "fertilizing" to fertilizingSchema(),
                    "pestControl" to pestControlSchema(),
                    "weeding" to weedingSchema(),
                    "harvest" to harvestSchema(),
                ),
                "required" to listOf("farmId", "cropId", "workType", "memo"),
            ),
        )
    }

    private fun plantingSchema() = objectProperty(
        "파종/정식 상세 (workType=PLANTING일 때만)",
        mapOf(
            "seedAmount" to mapOf("type" to "number", "description" to "파종량"),
            "seedAmountUnit" to enumProperty("파종량 단위", SeedAmountUnit.entries.map { it.name }),
            "seedlingCount" to mapOf("type" to "integer", "description" to "모종수"),
            "seedlingUnit" to enumProperty("모종수 단위", SeedlingUnit.entries.map { it.name }),
            "seedSource" to enumProperty("종자 출처", SeedSource.entries.map { it.name }),
            "seedPurchasePlace" to mapOf("type" to "string", "description" to "종자 구매처(seedSource=PURCHASED일 때 필수)"),
        ),
    )

    private fun wateringSchema() = objectProperty(
        "관수 상세 (workType=WATERING일 때만)",
        mapOf(
            "irrigationAmount" to enumProperty("관수량", IrrigationAmount.entries.map { it.name }),
            "irrigationMethod" to enumProperty("관수 방법", IrrigationMethod.entries.map { it.name }),
        ),
    )

    private fun fertilizingSchema() = objectProperty(
        "시비 상세 (workType=FERTILIZING일 때 필수)",
        mapOf(
            "materialName" to mapOf("type" to "string", "description" to "비료명(필수)"),
            "amount" to mapOf("type" to "number", "description" to "시비량(필수)"),
            "amountUnit" to enumProperty("시비량 단위(필수)", FertilizerAmountUnit.entries.map { it.name }),
            "applicationMethod" to enumProperty("시비 방법", FertilizingMethod.entries.map { it.name }),
        ),
    )

    private fun pestControlSchema() = objectProperty(
        "병해충 방제 상세 (workType=PEST_CONTROL일 때 필수)",
        mapOf(
            "pesticideName" to mapOf("type" to "string", "description" to "농약명(필수)"),
            "pesticideAmount" to mapOf("type" to "number", "description" to "농약량(필수)"),
            "pesticideAmountUnit" to enumProperty("농약량 단위(필수)", PesticideAmountUnit.entries.map { it.name }),
            "totalSprayAmount" to mapOf("type" to "number", "description" to "총 살포량(필수)"),
            "totalSprayAmountUnit" to enumProperty("총 살포량 단위(필수)", SprayAmountUnit.entries.map { it.name }),
            "pestTarget" to mapOf("type" to "string", "description" to "방제 대상 병해충"),
        ),
    )

    private fun weedingSchema() = objectProperty(
        "제초 상세 (workType=WEEDING일 때만)",
        mapOf("weedingMethod" to enumProperty("제초 방법", WeedingMethod.entries.map { it.name })),
    )

    private fun harvestSchema() = objectProperty(
        "수확 상세 (workType=HARVEST일 때 필수)",
        mapOf(
            "harvestAmount" to mapOf("type" to "number", "description" to "수확량(필수)"),
            "harvestAmountUnit" to enumProperty("수확량 단위(필수)", HarvestAmountUnit.entries.map { it.name }),
            "harvestSource" to enumProperty("수확 출처", HarvestSource.entries.map { it.name }),
            "growthPeriod" to mapOf("type" to "integer", "description" to "재배기간(필수)"),
            "growthPeriodUnit" to enumProperty("재배기간 단위(필수)", GrowthPeriodUnit.entries.map { it.name }),
        ),
    )

    private fun enumProperty(description: String, values: List<String>): Map<String, Any?> = mapOf(
        "type" to "string",
        "description" to description,
        "enum" to values,
    )

    private fun objectProperty(description: String, properties: Map<String, Any?>): Map<String, Any?> = mapOf(
        "type" to "object",
        "description" to description,
        "properties" to properties,
    )
}
