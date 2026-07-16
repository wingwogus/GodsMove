//
//  VoiceCandidateMapperTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("VoiceCandidateMapper")
struct VoiceCandidateMapperTests {

    private static let now = Date(timeIntervalSince1970: 1_784_160_000) // 2026-07-16 근처 고정값

    private func dto(
        _ json: String, pesticide: Pesticide? = nil, pest: Pest? = nil
    ) -> VoiceCandidateRequestDTO {
        VoiceCandidateMapper.makeCandidateDTO(
            from: VoiceCandidateMapper.parseToolArguments(json),
            resolvedPesticide: pesticide, resolvedPest: pest, now: Self.now
        )
    }

    private func prefill(
        _ json: String, pesticide: Pesticide? = nil, pest: Pest? = nil, missingFields: [String] = []
    ) -> VoiceRecordPrefill {
        VoiceCandidateMapper.makePrefill(
            from: VoiceCandidateMapper.parseToolArguments(json),
            resolvedPesticide: pesticide, resolvedPest: pest, missingFields: missingFields
        )
    }

    @Test("방제: 농약이 resolve되면 DTO에 pesticideId가 실린다")
    func pestControlResolved() {
        let farmId = UUID(), cropId = UUID()
        let pesticide = Pesticide(id: UUID(), itemName: "델란", brandName: "브랜드")
        let pest = Pest(id: UUID(), name: "탄저병")
        let json = """
        {"farmId":"\(farmId.uuidString)","cropId":"\(cropId.uuidString)","workType":"PEST_CONTROL",
         "memo":"소독약 살포",
         "pestControl":{"pesticideName":"델란","pesticideAmount":30,"pesticideAmountUnit":"ML",
                        "totalSprayAmount":20,"totalSprayAmountUnit":"L","pestTarget":"탄저병"}}
        """
        let result = dto(json, pesticide: pesticide, pest: pest)
        #expect(result.farmId == farmId)
        #expect(result.cropId == cropId)
        #expect(result.workType == "PEST_CONTROL")
        #expect(result.pestControl?.pesticideId == pesticide.id)
        #expect(result.pestControl?.pesticideAmount == 30)
        #expect(result.pestControl?.totalSprayAmount == 20)
        #expect(result.pestControl?.pestId == pest.id)
    }

    @Test("방제: resolve 실패 시 pestControl은 통째로 빠지지만 프리필에는 사용량이 남는다")
    func pestControlUnresolved() {
        let json = """
        {"workType":"PEST_CONTROL","memo":"m",
         "pestControl":{"pesticideName":"이상한약","pesticideAmount":30,"pesticideAmountUnit":"ML","totalSprayAmount":20}}
        """
        #expect(dto(json).pestControl == nil)

        let filled = prefill(json)
        #expect(filled.pesticide == nil)
        #expect(filled.pesticideAmount == 30)
        #expect(filled.totalSprayAmount == 20)
        #expect(filled.pesticideAmountUnit == .ml)
    }

    @Test("수확: growthPeriod 없으면 detail 생략, 있으면 isLastHarvest=false 기본값")
    func harvestRules() {
        let withoutPeriod = """
        {"workType":"HARVEST","memo":"m","harvest":{"harvestAmount":12.5,"harvestSource":"CULTIVATED"}}
        """
        #expect(dto(withoutPeriod).harvest == nil)
        #expect(prefill(withoutPeriod).harvestAmount == 12.5)

        let withPeriod = """
        {"workType":"HARVEST","memo":"m",
         "harvest":{"harvestAmount":12.5,"medicinalPart":"ROOT_BARK","harvestSource":"FORAGED","growthPeriod":18}}
        """
        let result = dto(withPeriod).harvest
        #expect(result?.growthPeriod == 18)
        #expect(result?.isLastHarvest == false)
        #expect(result?.harvestAmount == 12.5)
        #expect(result?.medicinalPart == "ROOT_BARK")
        #expect(result?.harvestSource == "FORAGED")
    }

    @Test("수확: 수확량 모름이면 amount는 비운다")
    func harvestAmountUnknown() {
        let json = """
        {"workType":"HARVEST","memo":"m",
         "harvest":{"harvestAmount":5,"harvestAmountUnknown":true,"growthPeriod":6}}
        """
        let result = dto(json).harvest
        #expect(result?.harvestAmountUnknown == true)
        #expect(result?.harvestAmount == nil)
    }

    @Test("심기: SEED는 씨앗 필드만, SEEDLING은 모종 필드만 남는다")
    func plantingPartition() {
        let seed = """
        {"workType":"PLANTING","memo":"m",
         "planting":{"plantingMethod":"SEED","seedAmount":200,"seedlingCount":30,"propagationMethod":"CUTTING"}}
        """
        let seedResult = dto(seed).planting
        #expect(seedResult?.seedAmount == 200)
        #expect(seedResult?.seedAmountUnit == "G")
        #expect(seedResult?.seedlingCount == nil)
        #expect(seedResult?.propagationMethod == nil)

        let seedling = """
        {"workType":"PLANTING","memo":"m",
         "planting":{"plantingMethod":"SEEDLING","seedAmount":200,"seedlingCount":30,"propagationMethod":"CUTTING"}}
        """
        let seedlingResult = dto(seedling).planting
        #expect(seedlingResult?.seedAmount == nil)
        #expect(seedlingResult?.seedlingCount == 30)
        #expect(seedlingResult?.seedlingUnit == "JU")
        #expect(seedlingResult?.propagationMethod == "CUTTING")
    }

    @Test("심기: plantingMethod가 없으면 detail 생략")
    func plantingWithoutMethod() {
        let json = #"{"workType":"PLANTING","memo":"m","planting":{"seedAmount":200}}"#
        #expect(dto(json).planting == nil)
        #expect(prefill(json).seedAmount == 200)
    }

    @Test("시비: materialName/amount/amountUnit 셋 다 있어야 detail이 실린다")
    func fertilizingRequiresAllRequired() {
        let missingUnit = """
        {"workType":"FERTILIZING","memo":"m","fertilizing":{"materialName":"복합비료","amount":500}}
        """
        #expect(dto(missingUnit).fertilizing == nil)
        #expect(prefill(missingUnit).fertilizerMaterialName == "복합비료")

        let complete = """
        {"workType":"FERTILIZING","memo":"m",
         "fertilizing":{"materialName":"복합비료","amount":500,"amountUnit":"G","applicationMethod":"SOIL"}}
        """
        let result = dto(complete).fertilizing
        #expect(result?.materialName == "복합비료")
        #expect(result?.amount == 500)
        #expect(result?.amountUnit == "G")
        #expect(result?.applicationMethod == "SOIL")
    }

    @Test("workType과 무관한 detail 객체는 무시한다")
    func mismatchedDetailDropped() {
        let json = """
        {"workType":"WATERING","memo":"m",
         "watering":{"irrigationAmount":"NORMAL","irrigationMethod":"DRIP"},
         "harvest":{"growthPeriod":6}}
        """
        let result = dto(json)
        #expect(result.watering?.irrigationAmount == "NORMAL")
        #expect(result.harvest == nil)
    }

    @Test("workedAt: 오프셋/Z/로컬 표기를 모두 받고, 없으면 now로 채운다")
    func workedAtNormalization() {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"

        let offset = VoiceCandidateMapper.parseWorkedAt("2026-07-15T09:00:00+09:00")
        #expect(offset != nil)

        let zulu = VoiceCandidateMapper.parseWorkedAt("2026-07-15T00:00:00Z")
        #expect(zulu == offset) // KST 09:00 == UTC 00:00

        let local = VoiceCandidateMapper.parseWorkedAt("2026-07-15T09:00:00")
        #expect(local != nil)

        let absent = dto(#"{"workType":"WATERING","memo":"m"}"#)
        #expect(absent.workedAt == formatter.string(from: Self.now))
    }

    @Test("모르는 enum 값은 그 필드만 nil이고 크래시하지 않는다")
    func unknownEnumTolerated() {
        let json = """
        {"workType":"BULLDOZING","memo":"m","watering":{"irrigationAmount":"FLOOD","irrigationMethod":"DRIP"}}
        """
        let candidate = VoiceCandidateMapper.parseToolArguments(json)
        #expect(candidate?.workType == nil)
        #expect(candidate?.watering?.amount == nil)
        #expect(candidate?.watering?.method == .drip)
        #expect(candidate?.memo == "m")
    }

    @Test("비JSON arguments는 후보 없음으로 처리되고 DTO는 workedAt만 채워진다")
    func junkArguments() {
        #expect(VoiceCandidateMapper.parseToolArguments("not json") == nil)

        let empty = VoiceCandidateMapper.makeCandidateDTO(
            from: nil, resolvedPesticide: nil, resolvedPest: nil, now: Self.now
        )
        #expect(empty.farmId == nil)
        #expect(empty.workType == nil)
        #expect(empty.workedAt != nil)
    }

    @Test("프리필은 missingFields를 그대로 담는다")
    func prefillCarriesMissingFields() {
        let filled = prefill(#"{"workType":"WATERING","memo":"m"}"#, missingFields: ["farmId", "cropId"])
        #expect(filled.missingFields == ["farmId", "cropId"])
        #expect(filled.workType == .watering)
        #expect(filled.memo == "m")
    }
}
