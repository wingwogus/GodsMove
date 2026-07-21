//
//  RecordDetailDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

// Wire-shape mirror of `GET /farming-records/{id}` → `RecordDetailResponse`. Every enum is decoded as a plain
// `String?` on purpose: the DEPLOYED detail response is still the old contract (e.g. `planting.propagationMethod`
// includes `SEED` with no separate `plantingMethod`; `fertilizing.amountUnit == [KG]`; watering method has
// `SPRINKLER`/`MANUAL`), while the compose form was pre-built against the newer dev contract. Decoding raw
// strings and resolving labels with tolerant lookups (below) keeps reads working today AND after dev deploys,
// without crashing on an unexpected value. See `docs/figma/record/2026-07-14-record-detail-implementation-plan.md`.
//
// Do NOT reuse the compose `RecordComposeModels` enums to decode this — they'd fail on deployed-only values.
//
// `farmId`/`cropId`/media `mediaId`/pest-control `pesticideId`·`pestId` are confirmed present and non-null in
// the backend response (`FarmingRecordResponses.RecordDetailResponse`, verified against source directly — see
// `docs/superpowers/plans/` record-edit plan), so they're decoded as required rather than optional/raw-tolerant.

struct RecordDetailResponseDTO: Decodable, Sendable {
    let id: UUID
    let farmId: UUID
    let farmName: String
    let cropId: UUID
    let cropName: String
    let workType: String
    let workedAt: String
    let memo: String?
    let weatherCondition: String
    let weatherTemperature: Int
    let images: [MediaResponseDTO]?

    let planting: PlantingDetailDTO?
    let watering: WateringDetailDTO?
    let fertilizing: FertilizingDetailDTO?
    let pestControl: PestControlDetailDTO?
    let weeding: WeedingDetailDTO?
    let harvest: HarvestDetailDTO?
    // pruning / etc: 상세 객체 없음

    struct PlantingDetailDTO: Decodable, Sendable {
        let plantingMethod: String?      // dev 계약(SEED/SEEDLING). 배포엔 없음 → nil.
        let propagationMethod: String?   // 배포엔 SEED 포함, dev엔 PURCHASED 포함.
        let seedAmount: Double?
        let seedAmountUnit: String?
        let seedlingCount: Int?
        let seedlingUnit: String?
    }

    struct WateringDetailDTO: Decodable, Sendable {
        let irrigationAmount: String?
        let irrigationMethod: String?
    }

    struct FertilizingDetailDTO: Decodable, Sendable {
        let materialName: String?
        let amount: Double?
        let amountUnit: String?
        let applicationMethod: String?
    }

    struct PestControlDetailDTO: Decodable, Sendable {
        let pesticideId: UUID?
        let pesticideName: String?
        let pestId: UUID?
        let pestName: String?
        let pesticideAmount: Double?
        let pesticideAmountUnit: String?
        let totalSprayAmount: Double?
        let totalSprayAmountUnit: String?
    }

    struct WeedingDetailDTO: Decodable, Sendable {
        let weedingMethod: String?
    }

    struct HarvestDetailDTO: Decodable, Sendable {
        let harvestAmount: Double?
        let amountUnknown: Bool?
        let growthPeriod: Int?
        let growthPeriodUnit: String?
        let harvestSource: String?
        let medicinalPart: String?
    }

    struct MediaResponseDTO: Decodable, Sendable {
        let mediaId: UUID
        let url: String
    }
}

// MARK: - Domain mapping

extension RecordDetailResponseDTO {
    func toDomain() -> RecordDetail {
        RecordDetail(
            id: id,
            workType: WorkType(rawValue: workType) ?? .etc,
            workedAt: RecordDateParser.date(from: workedAt),
            weatherCondition: weatherCondition,
            weatherTemperature: weatherTemperature,
            farmName: farmName,
            cropName: cropName,
            memo: memo ?? "",
            imageUrls: images?.map(\.url) ?? [],
            infoRows: RecordDetailLabels.infoRows(from: self)
        )
    }

    /// 기록 수정 폼 프리필. `toDomain()`의 표시용 라벨과 달리, 폼이 그대로 재제출할 수 있는 원본
    /// enum 코드/id를 보존한다. 알 수 없는 코드(배포·dev 계약 드리프트)는 해당 필드만 nil로
    /// 떨어뜨려 폼 진입 자체는 항상 성공한다 — `VoiceCandidateMapper`와 동일한 관용 원칙.
    func toEditPrefill() -> VoiceRecordPrefill {
        var prefill = VoiceRecordPrefill(
            farmId: farmId,
            cropId: cropId,
            workType: WorkType(rawValue: workType),
            workedAt: RecordDateParser.date(from: workedAt),
            memo: memo
        )

        if let p = planting {
            prefill.plantingMethod = (p.plantingMethod ?? p.propagationMethod).flatMap(PlantingMethod.init(rawValue:))
            prefill.seedAmount = p.seedAmount
            prefill.seedlingCount = p.seedlingCount
            if let propagationMethod = p.propagationMethod, propagationMethod != "SEED" {
                prefill.propagationMethod = PropagationMethod(rawValue: propagationMethod)
            }
        }

        if let w = watering {
            prefill.irrigationAmount = w.irrigationAmount.flatMap(IrrigationAmount.init(rawValue:))
            prefill.irrigationMethod = w.irrigationMethod.flatMap(IrrigationMethod.init(rawValue:))
        }

        if let f = fertilizing {
            prefill.fertilizerMaterialName = f.materialName
            prefill.fertilizerAmount = f.amount
            prefill.fertilizerAmountUnit = f.amountUnit.flatMap(FertilizerAmountUnit.init(rawValue:))
            prefill.fertilizingMethod = f.applicationMethod.flatMap(FertilizingMethod.init(rawValue:))
        }

        if let pc = pestControl {
            if let pesticideId = pc.pesticideId {
                prefill.pesticide = Pesticide(id: pesticideId, itemName: pc.pesticideName ?? "", brandName: "")
            }
            prefill.pesticideAmount = pc.pesticideAmount
            prefill.pesticideAmountUnit = pc.pesticideAmountUnit.flatMap(PesticideAmountUnit.init(rawValue:))
            prefill.totalSprayAmount = pc.totalSprayAmount
            if let pestId = pc.pestId {
                prefill.pest = Pest(id: pestId, name: pc.pestName ?? "")
            }
        }

        if let wd = weeding {
            prefill.weedingMethod = wd.weedingMethod.flatMap(WeedingMethod.init(rawValue:))
        }

        if let h = harvest {
            prefill.growthPeriod = h.growthPeriod
            prefill.harvestAmount = h.harvestAmount
            prefill.harvestAmountUnknown = h.amountUnknown ?? false
            prefill.medicinalPart = h.medicinalPart.flatMap(MedicinalPart.init(rawValue:))
        }

        prefill.existingPhotos = images?.map { ExistingPhoto(mediaId: $0.mediaId, url: $0.url) } ?? []
        return prefill
    }
}

/// Tolerant enum→label lookups + '작업 정보' row assembly. Unknown values fall back to the raw string rather
/// than crashing, so a contract drift (배포↔dev) degrades gracefully instead of failing the whole screen.
enum RecordDetailLabels {
    static func infoRows(from dto: RecordDetailResponseDTO) -> [RecordInfoRow] {
        var rows: [RecordInfoRow] = []
        func add(_ label: String, _ value: String?) {
            guard let value, !value.trimmingCharacters(in: .whitespaces).isEmpty else { return }
            rows.append(RecordInfoRow(label: label, value: value))
        }

        if let p = dto.planting {
            let isSeed = (p.plantingMethod ?? p.propagationMethod) == "SEED"
            add("심기 방법", isSeed ? "씨앗 심기" : "모종 심기")
            if isSeed {
                add("심은 씨앗량", amount(p.seedAmount, p.seedAmountUnit))
            } else {
                if let prop = p.propagationMethod, prop != "SEED" {
                    add("모종 번식법", propagationMethodLabel(prop))
                }
                add("심은 모종 수", count(p.seedlingCount, p.seedlingUnit))
            }
        }

        if let w = dto.watering {
            add("물의 양", w.irrigationAmount.map(irrigationAmountLabel))
            add("진행 방식", w.irrigationMethod.map(irrigationMethodLabel))
        }

        if let f = dto.fertilizing {
            add("사용 비료", f.materialName)
            add("비료 사용량", amount(f.amount, f.amountUnit))
            add("진행 방식", f.applicationMethod.map(fertilizingMethodLabel))
        }

        if let pc = dto.pestControl {
            add("사용 농약", pc.pesticideName)
            add("농약 사용량", amount(pc.pesticideAmount, pc.pesticideAmountUnit))
            add("총 살포량", amount(pc.totalSprayAmount, pc.totalSprayAmountUnit))
            add("대상 병해충", pc.pestName)
        }

        if let wd = dto.weeding {
            add("진행 방식", wd.weedingMethod.map(weedingMethodLabel))
        }

        if let h = dto.harvest {
            if let g = h.growthPeriod {
                add("재배 기간", "\(g)" + growthPeriodUnitLabel(h.growthPeriodUnit))
            }
            if h.amountUnknown == true {
                add("수확량", "잘 모르겠음")
            } else if let a = h.harvestAmount {
                // 수확량 단위는 배포 스키마에 없음(디자인/제품 기준 kg 표기). 서버가 단위를 제공하면 교체.
                add("수확량", "\(number(a))kg")
            }
            add("수확 부위", h.medicinalPart.map(medicinalPartLabel))
        }

        return rows
    }

    // MARK: value formatting

    /// `50.0` → "50", `1.5` → "1.5". 단위 없는 정수 표기를 위해 후행 0 제거.
    static func number(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(value)
    }

    private static func amount(_ value: Double?, _ unit: String?) -> String? {
        guard let value else { return nil }
        return number(value) + unitLabel(unit)
    }

    private static func count(_ value: Int?, _ unit: String?) -> String? {
        guard let value else { return nil }
        return "\(value)" + unitLabel(unit)
    }

    /// 공통 단위 코드 → 표기. 알 수 없으면 원문.
    private static func unitLabel(_ raw: String?) -> String {
        switch raw {
        case "G": return "g"
        case "KG": return "kg"
        case "ML": return "ml"
        case "L": return "L"
        case "JU": return "주"
        case .some(let other): return other
        case nil: return ""
        }
    }

    // MARK: enum labels (배포·dev 값 모두 커버, 미상은 원문)

    private static func propagationMethodLabel(_ raw: String) -> String {
        switch raw {
        case "CUTTING": return "꺾꽂이"
        case "GRAFTING": return "접붙이기"
        case "LAYERING": return "휘묻이"
        case "DIVISION": return "포기나누기"
        case "TISSUE_CULTURE": return "조직 배양"
        case "PURCHASED": return "시판 구매"
        default: return raw
        }
    }

    private static func irrigationAmountLabel(_ raw: String) -> String {
        switch raw {
        case "LOW": return "조금"
        case "NORMAL": return "보통"
        case "SUFFICIENT": return "많이"
        default: return raw
        }
    }

    private static func irrigationMethodLabel(_ raw: String) -> String {
        switch raw {
        case "DRIP": return "점적"
        case "SPRAYING", "SPRINKLER": return "살수"
        case "MANUAL": return "수동"
        case "ETC": return "기타"
        default: return raw
        }
    }

    private static func fertilizingMethodLabel(_ raw: String) -> String {
        switch raw {
        case "SOIL": return "토양에 주기"
        case "FOLIAR": return "엽면에 뿌리기"
        default: return raw
        }
    }

    private static func weedingMethodLabel(_ raw: String) -> String {
        switch raw {
        case "HAND": return "손으로 뽑기"
        case "MACHINE": return "예초기 사용"
        case "MULCHING": return "멀칭 (비닐 덮기)"
        case "HERBICIDE": return "제초제 사용"
        default: return raw
        }
    }

    private static func growthPeriodUnitLabel(_ raw: String?) -> String {
        switch raw {
        case "YEAR": return "년"
        case "MONTH": return "개월"
        default: return raw ?? ""
        }
    }

    private static func medicinalPartLabel(_ raw: String) -> String {
        switch raw {
        case "WHOLE_HERB": return "전초"
        case "ROOT_BARK": return "뿌리·껍질"
        case "RHIZOME": return "뿌리줄기"
        case "LEAF": return "잎"
        case "FLOWER": return "꽃"
        case "FRUIT": return "열매·과실"
        case "SEED": return "종자"
        case "STEM_BRANCH": return "줄기·가지"
        case "UNKNOWN": return "기타"
        default: return raw
        }
    }
}
