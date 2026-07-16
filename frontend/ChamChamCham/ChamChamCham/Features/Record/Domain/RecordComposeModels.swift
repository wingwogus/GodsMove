//
//  RecordComposeModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

// MARK: - workType 상세 enum
//
// origin dev(`feat/farming-record-refine`, PR #19) 계약 기준. 배포 Swagger가 재동기화되기 전까지
// 배포 API와 다를 수 있음 — `docs/figma/record/2026-07-13-record-backend-conflicts.md` 참고.
// 라벨은 프론트 확정 워딩(백엔드 label과 다를 수 있으므로 코드만 계약).

/// 심기 방법 (재배법). 씨앗/모종 2택.
enum PlantingMethod: String, Sendable, Hashable, CaseIterable {
    case seed = "SEED"
    case seedling = "SEEDLING"
    var label: String { self == .seed ? "씨앗 심기" : "모종 심기" }
}

/// 모종 번식법. 모종 심기일 때만 노출.
enum PropagationMethod: String, Sendable, Hashable, CaseIterable {
    case cutting = "CUTTING"
    case grafting = "GRAFTING"
    case layering = "LAYERING"
    case division = "DIVISION"
    case tissueCulture = "TISSUE_CULTURE"
    case purchased = "PURCHASED"
    var label: String {
        switch self {
        case .cutting: "꺾꽂이"
        case .grafting: "접붙이기"
        case .layering: "휘묻이"
        case .division: "포기나누기"
        case .tissueCulture: "조직 배양"
        case .purchased: "시판 구매"
        }
    }
}

enum SeedAmountUnit: String, Sendable, Hashable, CaseIterable {
    case g = "G"
    var label: String { "g" }
}

enum SeedlingUnit: String, Sendable, Hashable, CaseIterable {
    case ju = "JU"
    var label: String { "주" }
}

/// 물주기 물의 양.
enum IrrigationAmount: String, Sendable, Hashable, CaseIterable {
    case low = "LOW"
    case normal = "NORMAL"
    case sufficient = "SUFFICIENT"
    var label: String {
        switch self {
        case .low: "조금"
        case .normal: "보통"
        case .sufficient: "많이"
        }
    }
}

/// 물주기 진행 방식.
enum IrrigationMethod: String, Sendable, Hashable, CaseIterable {
    case drip = "DRIP"
    case spraying = "SPRAYING"
    case etc = "ETC"
    var label: String {
        switch self {
        case .drip: "점적"
        case .spraying: "살수"
        case .etc: "기타"
        }
    }
}

/// 비료 사용량 단위.
enum FertilizerAmountUnit: String, Sendable, Hashable, CaseIterable {
    case g = "G"
    case ml = "ML"
    var label: String { self == .g ? "g" : "ml" }
}

/// 비료 진행 방식.
enum FertilizingMethod: String, Sendable, Hashable, CaseIterable {
    case soil = "SOIL"
    case foliar = "FOLIAR"
    var label: String { self == .soil ? "토양에 주기" : "엽면에 뿌리기" }
}

/// 농약 사용량 단위.
enum PesticideAmountUnit: String, Sendable, Hashable, CaseIterable {
    case ml = "ML"
    case g = "G"
    var label: String { self == .ml ? "ml" : "g" }
}

/// 총 살포량 단위.
enum SprayAmountUnit: String, Sendable, Hashable, CaseIterable {
    case l = "L"
    var label: String { "L" }
}

/// 잡초 관리 진행 방식.
enum WeedingMethod: String, Sendable, Hashable, CaseIterable {
    case hand = "HAND"
    case machine = "MACHINE"
    case mulching = "MULCHING"
    case herbicide = "HERBICIDE"
    var label: String {
        switch self {
        case .hand: "손으로 뽑기"
        case .machine: "예초기 사용"
        case .mulching: "멀칭 (비닐 덮기)"
        case .herbicide: "제초제 사용"
        }
    }
}

/// 재배 기간 단위.
enum GrowthPeriodUnit: String, Sendable, Hashable, CaseIterable {
    case year = "YEAR"
    case month = "MONTH"
    var label: String { self == .year ? "년" : "개월" }
}

/// 수확 부위 (`CropUsePartCategory`).
enum MedicinalPart: String, Sendable, Hashable, CaseIterable {
    case wholeHerb = "WHOLE_HERB"
    case rootBark = "ROOT_BARK"
    case rhizome = "RHIZOME"
    case leaf = "LEAF"
    case flower = "FLOWER"
    case fruit = "FRUIT"
    case seed = "SEED"
    case stemBranch = "STEM_BRANCH"
    case unknown = "UNKNOWN"
    var label: String {
        switch self {
        case .wholeHerb: "전초"
        case .rootBark: "뿌리·껍질"
        case .rhizome: "뿌리줄기"
        case .leaf: "잎"
        case .flower: "꽃"
        case .fruit: "열매·과실"
        case .seed: "종자"
        case .stemBranch: "줄기·가지"
        case .unknown: "기타"
        }
    }
}

/// 기록 입력 방식.
enum EntryMode: String, Sendable, Hashable {
    case manual = "MANUAL"
    case voice = "VOICE"
}

// MARK: - 폼 보조 도메인

/// 농지 + 그 농지의 작물. `GET /members/me/farm-crops` 그룹 응답.
struct FarmWithCrops: Identifiable, Hashable, Sendable {
    var id: UUID { farmId }
    let farmId: UUID
    let farmName: String
    let crops: [ActiveCrop]
}

/// 농약 카탈로그 항목 (`GET /pesticides`).
struct Pesticide: Identifiable, Hashable, Sendable {
    let id: UUID
    let itemName: String
    let brandName: String
}

/// 병해충 (`GET /pesticides/{id}/pests`).
struct Pest: Identifiable, Hashable, Sendable {
    let id: UUID
    let name: String
}

