//
//  VoiceSessionModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// 음성 세션 화면 상태. BR-STATE-001의 상태 머신을 클라이언트 관점으로 옮긴 것으로,
/// `conversing`(RECORDING)/`processing`(PROCESSING)은 서버에 없는 클라이언트 전용 단계다
/// (서버는 CREATED → WAITING_CONFIRMATION으로 한 번에 점프). COMPLETED는 검토 화면의
/// confirm 성공이 담당하므로 이 enum에 없다.
enum VoiceSessionPhase: Equatable, Sendable {
    case idle
    /// 마이크 권한 확인 + 세션 발급 + WebRTC 연결 중 (서버: CREATED)
    case preparing
    /// 대화 중 (BR: RECORDING). 마이크 버튼은 종료가 아니라 mute 토글.
    case conversing(muted: Bool)
    /// 대화 종료 후 후보 정제 + turns 제출 중 (BR: PROCESSING)
    case processing
    /// 검토 화면으로 핸드오프 완료 (서버: WAITING_CONFIRMATION)
    case reviewing
    /// BR-VOICE-008: 비정상 종료. 기존 세션은 되살리지 않고 새 세션으로만 재시작한다.
    case failed(message: String)
    case cancelled
}

/// `POST /voice-sessions` 성공 결과 중 클라이언트가 실제로 쓰는 값.
struct VoiceSessionInfo: Sendable, Equatable {
    let sessionId: UUID
    /// OpenAI Realtime WebRTC 연결용 단기 토큰(ek_...). 백엔드 API 토큰과 무관.
    let clientSecret: String
    let model: String
    /// 대화 시간 한도(초). 서버는 대화 중 개입할 수 없어 이 값을 클라이언트가 강제한다.
    /// OpenAI 토큰은 이보다 30초 뒤(하드 만료)에 끊기므로, 클라이언트가 먼저 우아하게 종료한다.
    let maxDurationSeconds: Int
}

enum VoiceTurnRole: String, Sendable, Equatable {
    case user = "USER"
    case assistant = "ASSISTANT"
}

/// `PATCH /voice-sessions/{id}/turns`로 보낼 대화 턴 하나.
struct VoiceTurn: Sendable, Equatable {
    let role: VoiceTurnRole
    let content: String
    /// tool call 원문 JSON. 마지막 ASSISTANT 턴에만 실린다.
    let extractedFields: String?

    init(role: VoiceTurnRole, content: String, extractedFields: String? = nil) {
        self.role = role
        self.content = content
        self.extractedFields = extractedFields
    }
}

/// 대화 중 말풍선 하나. Realtime의 `conversation.item.added`가 슬롯을 만들고
/// 전사 이벤트가 나중에 텍스트를 채우므로(사용자 전사는 비동기로 늦게 옴),
/// itemId 기준으로 순서를 고정한다.
struct VoiceTranscriptItem: Identifiable, Sendable, Equatable {
    let itemId: String
    let role: VoiceTurnRole
    var text: String

    var id: String { itemId }
}

/// voice-sessions 전용 에러. 연동 가이드의 VOICE_00x 코드 매핑.
enum VoiceSessionError: Error, Equatable {
    /// VOICE_001(404) — 세션이 없거나 남의 세션.
    case notFound
    /// VOICE_002(409) — 상태 불일치. "이미 처리됨"으로 취급하고 절대 재시도하지 않는다.
    case alreadyProcessed
    /// VOICE_003(503) — OpenAI 쪽 장애. "잠시 후 다시 시도" 안내.
    case providerUnavailable
}

/// AI 후보를 검토 화면(RecordComposeViewModel)에 채워 넣기 위한 평탄화 프리필.
/// candidate DTO 정제에서 탈락한 원문 값도 유지한다 — 사용자가 검토에서 이어 채운다.
struct VoiceRecordPrefill: Sendable, Hashable {
    var farmId: UUID?
    var cropId: UUID?
    var workType: WorkType?
    var workedAt: Date?
    var memo: String?
    var plantingMethod: PlantingMethod?
    var seedAmount: Double?
    var seedlingCount: Int?
    var propagationMethod: PropagationMethod?
    var irrigationAmount: IrrigationAmount?
    var irrigationMethod: IrrigationMethod?
    var fertilizerMaterialName: String?
    var fertilizerAmount: Double?
    var fertilizerAmountUnit: FertilizerAmountUnit?
    var fertilizingMethod: FertilizingMethod?
    var pesticide: Pesticide?
    var pesticideAmount: Double?
    var pesticideAmountUnit: PesticideAmountUnit?
    var totalSprayAmount: Double?
    var pest: Pest?
    var weedingMethod: WeedingMethod?
    var growthPeriod: Int?
    var harvestAmount: Double?
    var harvestAmountUnknown: Bool = false
    var medicinalPart: MedicinalPart?
    /// 서버 판정 누락 필드(`farmId`/`cropId`/`workType`/`workedAt`/`detail` 리터럴).
    /// 비어 있지 않으면 검토 화면이 진입 즉시 검증 문구를 노출한다(BR-EXCEPTION-005).
    var missingFields: [String] = []
}

/// 대화 화면 → 검토 화면 핸드오프. `navigationDestination(item:)` 바인딩용.
struct VoiceReviewHandoff: Identifiable, Hashable, Sendable {
    let sessionId: UUID
    let prefill: VoiceRecordPrefill

    var id: UUID { sessionId }
}
