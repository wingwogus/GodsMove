//
//  VoiceCandidateMapper.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// `save_farming_record` tool arguments(백엔드 `FarmingRecordVoiceToolSchema`)의 관용 파싱 결과.
/// 모르는 enum 값이나 형식 오류는 해당 필드만 nil로 떨어뜨리고 절대 실패시키지 않는다 —
/// AI 출력은 계약 밖 값을 낼 수 있고, 빠진 값은 검토 화면에서 사용자가 채운다(BR-AI-005).
struct VoiceToolCandidate: Sendable, Equatable {
    var farmId: UUID?
    var cropId: UUID?
    var workType: WorkType?
    var workedAt: Date?
    var memo: String?
    var planting: Planting?
    var watering: Watering?
    var fertilizing: Fertilizing?
    var pestControl: PestControl?
    var weeding: Weeding?
    var harvest: Harvest?

    struct Planting: Sendable, Equatable {
        var method: PlantingMethod?
        var seedAmount: Double?
        var seedlingCount: Int?
        var propagationMethod: PropagationMethod?
    }

    struct Watering: Sendable, Equatable {
        var amount: IrrigationAmount?
        var method: IrrigationMethod?
    }

    struct Fertilizing: Sendable, Equatable {
        var materialName: String?
        var amount: Double?
        var amountUnit: FertilizerAmountUnit?
        var applicationMethod: FertilizingMethod?
    }

    struct PestControl: Sendable, Equatable {
        /// 도구 계약은 농약을 id가 아닌 이름 문자열로 받는다. 서버 detail DTO는
        /// pesticideId가 필수라 카탈로그 검색으로 resolve해야 한다.
        var pesticideName: String?
        var pesticideAmount: Double?
        var pesticideAmountUnit: PesticideAmountUnit?
        var totalSprayAmount: Double?
        var pestTarget: String?
    }

    struct Weeding: Sendable, Equatable {
        var method: WeedingMethod?
    }

    struct Harvest: Sendable, Equatable {
        var amount: Double?
        var amountUnknown: Bool = false
        var medicinalPart: MedicinalPart?
        var source: String?
        var growthPeriod: Int?
        var isLastHarvest: Bool?
    }
}

/// tool arguments → turns 제출용 candidate DTO / 검토 화면 프리필 변환.
///
/// candidate DTO 정제 규칙(서버 `CandidateRequest`가 detail에 수동 저장과 같은 필수 필드를
/// 요구하기 때문): 필수값을 못 채우는 detail은 객체째 생략한다. 생략해도 값은 프리필에
/// 남아 검토 화면에는 그대로 채워진다.
enum VoiceCandidateMapper {

    static func parseToolArguments(_ json: String) -> VoiceToolCandidate? {
        guard let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any] else {
            return nil
        }
        var candidate = VoiceToolCandidate()
        candidate.farmId = uuid(object["farmId"])
        candidate.cropId = uuid(object["cropId"])
        candidate.workType = enumValue(object["workType"], WorkType.init(rawValue:))
        candidate.workedAt = (object["workedAt"] as? String).flatMap(parseWorkedAt)
        candidate.memo = nonBlank(object["memo"])

        if let planting = object["planting"] as? [String: Any] {
            candidate.planting = VoiceToolCandidate.Planting(
                method: enumValue(planting["plantingMethod"], PlantingMethod.init(rawValue:)),
                seedAmount: number(planting["seedAmount"]),
                seedlingCount: integer(planting["seedlingCount"]),
                propagationMethod: enumValue(planting["propagationMethod"], PropagationMethod.init(rawValue:))
            )
        }
        if let watering = object["watering"] as? [String: Any] {
            candidate.watering = VoiceToolCandidate.Watering(
                amount: enumValue(watering["irrigationAmount"], IrrigationAmount.init(rawValue:)),
                method: enumValue(watering["irrigationMethod"], IrrigationMethod.init(rawValue:))
            )
        }
        if let fertilizing = object["fertilizing"] as? [String: Any] {
            candidate.fertilizing = VoiceToolCandidate.Fertilizing(
                materialName: nonBlank(fertilizing["materialName"]),
                amount: number(fertilizing["amount"]),
                amountUnit: enumValue(fertilizing["amountUnit"], FertilizerAmountUnit.init(rawValue:)),
                applicationMethod: enumValue(fertilizing["applicationMethod"], FertilizingMethod.init(rawValue:))
            )
        }
        if let pestControl = object["pestControl"] as? [String: Any] {
            candidate.pestControl = VoiceToolCandidate.PestControl(
                pesticideName: nonBlank(pestControl["pesticideName"]),
                pesticideAmount: number(pestControl["pesticideAmount"]),
                pesticideAmountUnit: enumValue(pestControl["pesticideAmountUnit"], PesticideAmountUnit.init(rawValue:)),
                totalSprayAmount: number(pestControl["totalSprayAmount"]),
                pestTarget: nonBlank(pestControl["pestTarget"])
            )
        }
        if let weeding = object["weeding"] as? [String: Any] {
            candidate.weeding = VoiceToolCandidate.Weeding(
                method: enumValue(weeding["weedingMethod"], WeedingMethod.init(rawValue:))
            )
        }
        if let harvest = object["harvest"] as? [String: Any] {
            candidate.harvest = VoiceToolCandidate.Harvest(
                amount: number(harvest["harvestAmount"]),
                amountUnknown: harvest["harvestAmountUnknown"] as? Bool ?? false,
                medicinalPart: enumValue(harvest["medicinalPart"], MedicinalPart.init(rawValue:)),
                source: nonBlank(harvest["harvestSource"]),
                growthPeriod: integer(harvest["growthPeriod"]),
                isLastHarvest: harvest["isLastHarvest"] as? Bool
            )
        }
        return candidate
    }

    /// turns 제출용 candidate. `now`는 workedAt 부재 시 기본값(도구 계약: 사용자가 상대
    /// 시간을 말하지 않으면 생략 → 현재 시각).
    static func makeCandidateDTO(
        from candidate: VoiceToolCandidate?,
        resolvedPesticide: Pesticide?,
        resolvedPest: Pest?,
        now: Date
    ) -> VoiceCandidateRequestDTO {
        let workType = candidate?.workType

        var planting: PlantingDetailRequestDTO?
        if workType == .planting, let detail = candidate?.planting, let method = detail.method {
            let isSeed = method == .seed
            planting = PlantingDetailRequestDTO(
                plantingMethod: method.rawValue,
                seedAmount: isSeed ? detail.seedAmount : nil,
                seedAmountUnit: isSeed && detail.seedAmount != nil ? SeedAmountUnit.g.rawValue : nil,
                seedlingCount: isSeed ? nil : detail.seedlingCount,
                seedlingUnit: !isSeed && detail.seedlingCount != nil ? SeedlingUnit.ju.rawValue : nil,
                propagationMethod: isSeed ? nil : detail.propagationMethod?.rawValue
            )
        }

        var watering: WateringDetailRequestDTO?
        if workType == .watering, let detail = candidate?.watering {
            watering = WateringDetailRequestDTO(
                irrigationAmount: detail.amount?.rawValue,
                irrigationMethod: detail.method?.rawValue
            )
        }

        var fertilizing: FertilizingDetailRequestDTO?
        if workType == .fertilizing, let detail = candidate?.fertilizing,
           let materialName = detail.materialName, let amount = detail.amount, let unit = detail.amountUnit {
            fertilizing = FertilizingDetailRequestDTO(
                materialName: materialName,
                amount: amount,
                amountUnit: unit.rawValue,
                applicationMethod: detail.applicationMethod?.rawValue
            )
        }

        var pestControl: PestControlDetailRequestDTO?
        if workType == .pestControl, let detail = candidate?.pestControl,
           let pesticide = resolvedPesticide,
           let amount = detail.pesticideAmount, let spray = detail.totalSprayAmount {
            pestControl = PestControlDetailRequestDTO(
                pesticideId: pesticide.id,
                pesticideAmount: amount,
                pesticideAmountUnit: (detail.pesticideAmountUnit ?? .ml).rawValue,
                totalSprayAmount: spray,
                totalSprayAmountUnit: SprayAmountUnit.ml.rawValue,
                pestId: resolvedPest?.id
            )
        }

        var weeding: WeedingDetailRequestDTO?
        if workType == .weeding, let detail = candidate?.weeding {
            weeding = WeedingDetailRequestDTO(weedingMethod: detail.method?.rawValue)
        }

        var harvest: HarvestDetailRequestDTO?
        if workType == .harvest, let detail = candidate?.harvest, let growthPeriod = detail.growthPeriod {
            harvest = HarvestDetailRequestDTO(
                harvestAmount: detail.amountUnknown ? nil : detail.amount,
                harvestAmountUnknown: detail.amountUnknown,
                medicinalPart: detail.medicinalPart?.rawValue,
                harvestSource: detail.source ?? "CULTIVATED",
                growthPeriod: growthPeriod,
                growthPeriodUnit: GrowthPeriodUnit.month.rawValue, // 도구 계약: 재배기간은 개월 고정
                isLastHarvest: detail.isLastHarvest ?? false
            )
        }

        return VoiceCandidateRequestDTO(
            farmId: candidate?.farmId,
            cropId: candidate?.cropId,
            workType: workType?.rawValue,
            workedAt: Self.dateTimeFormatter.string(from: candidate?.workedAt ?? now),
            memo: candidate?.memo,
            planting: planting,
            watering: watering,
            fertilizing: fertilizing,
            pestControl: pestControl,
            weeding: weeding,
            harvest: harvest
        )
    }

    /// 검토 화면(RecordComposeViewModel) 프리필. DTO 정제에서 탈락한 원문 값
    /// (예: resolve 실패한 농약의 사용량)도 그대로 실어 사용자가 이어서 채우게 한다.
    static func makePrefill(
        from candidate: VoiceToolCandidate?,
        resolvedPesticide: Pesticide?,
        resolvedPest: Pest?,
        missingFields: [String]
    ) -> VoiceRecordPrefill {
        VoiceRecordPrefill(
            farmId: candidate?.farmId,
            cropId: candidate?.cropId,
            workType: candidate?.workType,
            workedAt: candidate?.workedAt,
            memo: candidate?.memo,
            plantingMethod: candidate?.planting?.method,
            seedAmount: candidate?.planting?.seedAmount,
            seedlingCount: candidate?.planting?.seedlingCount,
            propagationMethod: candidate?.planting?.propagationMethod,
            irrigationAmount: candidate?.watering?.amount,
            irrigationMethod: candidate?.watering?.method,
            fertilizerMaterialName: candidate?.fertilizing?.materialName,
            fertilizerAmount: candidate?.fertilizing?.amount,
            fertilizerAmountUnit: candidate?.fertilizing?.amountUnit,
            fertilizingMethod: candidate?.fertilizing?.applicationMethod,
            pesticide: resolvedPesticide,
            pesticideAmount: candidate?.pestControl?.pesticideAmount,
            pesticideAmountUnit: candidate?.pestControl?.pesticideAmountUnit,
            totalSprayAmount: candidate?.pestControl?.totalSprayAmount,
            pest: resolvedPest,
            weedingMethod: candidate?.weeding?.method,
            growthPeriod: candidate?.harvest?.growthPeriod,
            harvestAmount: candidate?.harvest?.amount,
            harvestAmountUnknown: candidate?.harvest?.amountUnknown ?? false,
            medicinalPart: candidate?.harvest?.medicinalPart,
            isLastHarvest: candidate?.harvest?.isLastHarvest ?? false,
            missingFields: missingFields
        )
    }

    // MARK: - 파싱 헬퍼

    private static func uuid(_ value: Any?) -> UUID? {
        (value as? String).flatMap(UUID.init(uuidString:))
    }

    private static func enumValue<T>(_ value: Any?, _ initializer: (String) -> T?) -> T? {
        (value as? String).flatMap(initializer)
    }

    private static func nonBlank(_ value: Any?) -> String? {
        guard let string = value as? String else { return nil }
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func number(_ value: Any?) -> Double? {
        (value as? NSNumber)?.doubleValue
    }

    private static func integer(_ value: Any?) -> Int? {
        (value as? NSNumber)?.intValue
    }

    /// AI는 오프셋 포함 ISO-8601(Z 포함)을 낼 수도, 로컬 표기를 낼 수도 있다. 셋 다 받는다.
    /// ISO8601DateFormatter는 Sendable이 아니라 정적 보관이 안 되므로 DateFormatter로 통일.
    static func parseWorkedAt(_ raw: String) -> Date? {
        isoOffsetFormatter.date(from: raw)
            ?? isoFractionalFormatter.date(from: raw)
            ?? dateTimeFormatter.date(from: raw)
    }

    private static let isoOffsetFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ssXXXXX"
        return formatter
    }()

    private static let isoFractionalFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX"
        return formatter
    }()

    /// 서버 LocalDateTime 표기(기기 로컬 시간대) — RecordComposeViewModel과 동일 규칙.
    private static let dateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return formatter
    }()
}
