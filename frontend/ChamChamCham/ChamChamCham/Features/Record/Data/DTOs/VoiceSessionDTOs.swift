//
//  VoiceSessionDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

// MARK: - POST /voice-sessions 응답

/// `expiresAt`/`farms`/`cropsByFarm`은 현재 클라이언트에서 쓰지 않아 디코딩하지 않는다.
/// tool 스키마가 farmId/cropId를 회원 실소유 값으로 enum 제한하고, 검토 폼이 자체적으로
/// farm-crops를 다시 로드하기 때문이다. `maxDurationSeconds`는 클라이언트가 대화 시간을
/// 강제해야 하므로(서버는 대화 중 개입 불가) 디코딩한다. `maxRounds`는 하드 강제가 아니라
/// 프롬프트 self-pacing 값이라 UI에 노출하지 않으므로 디코딩하지 않는다.
struct VoiceSessionCreatedResponseDTO: Decodable, Sendable {
    let sessionId: UUID
    let clientSecret: String
    let model: String
    let maxDurationSeconds: Int

    func toDomain() -> VoiceSessionInfo {
        VoiceSessionInfo(
            sessionId: sessionId,
            clientSecret: clientSecret,
            model: model,
            maxDurationSeconds: maxDurationSeconds
        )
    }
}

// MARK: - PATCH /voice-sessions/{id}/turns

struct SubmitTurnsRequestDTO: Encodable, Sendable {
    let turns: [VoiceTurnRequestDTO]
    let candidate: VoiceCandidateRequestDTO
}

struct VoiceTurnRequestDTO: Encodable, Sendable {
    let role: String
    let content: String
    let extractedFields: String?
}

/// turns 제출 시점의 후보 초안. 서버 `CandidateRequest`는 전 필드 nullable이지만 detail
/// 객체는 수동 저장과 같은 DTO를 재사용해 필수 필드가 있으므로(예: pestControl.pesticideId),
/// 못 채우는 detail은 필드를 nil로 두는 게 아니라 객체째 생략해야 한다 — `VoiceCandidateMapper` 참고.
/// 날씨는 음성 AI가 수집하지 않으므로(도구 스키마에 없음) 여기 필드 자체가 없다.
struct VoiceCandidateRequestDTO: Encodable, Sendable, Equatable {
    let farmId: UUID?
    let cropId: UUID?
    let workType: String?
    let workedAt: String?   // LocalDateTime "yyyy-MM-dd'T'HH:mm:ss"
    let memo: String?
    let planting: PlantingDetailRequestDTO?
    let watering: WateringDetailRequestDTO?
    let fertilizing: FertilizingDetailRequestDTO?
    let pestControl: PestControlDetailRequestDTO?
    let weeding: WeedingDetailRequestDTO?
    let harvest: HarvestDetailRequestDTO?
}

/// turns 응답. 서버가 되돌려주는 echo `candidate`는 클라이언트가 방금 만든 값이라 디코딩하지
/// 않는다(응답 쪽 detail 필드명이 요청과 달라 — 예: harvestAmountUnknown ↔ amountUnknown —
/// 불필요한 결합만 생긴다). 검토 프리필은 클라이언트가 보관한 원본을 쓴다.
struct VoiceProcessedResponseDTO: Decodable, Sendable {
    let sessionId: UUID
    let status: String
    let missingFields: [String]
}

// MARK: - POST /voice-sessions/{id}/confirm · cancel

struct VoiceConfirmedResponseDTO: Decodable, Sendable {
    let sessionId: UUID
    let recordId: UUID
    let workType: String
}

struct VoiceCancelledResponseDTO: Decodable, Sendable {
    let sessionId: UUID
    let status: String
}
