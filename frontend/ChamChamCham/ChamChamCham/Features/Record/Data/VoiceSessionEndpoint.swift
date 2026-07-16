//
//  VoiceSessionEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// 음성 세션 라이프사이클 API. 오디오/실시간 대화는 여기를 거치지 않고 iOS ↔ OpenAI
/// WebRTC 직결로 처리되며(`WebRTCVoiceTransport`), 백엔드는 세션 발급과 결과 기록만 담당한다.
/// `submitTurns`는 턴마다가 아니라 대화 전체가 끝난 뒤 한 번 호출하는 API다.
enum VoiceSessionEndpoint: Endpoint {
    case create
    case submitTurns(sessionId: UUID, SubmitTurnsRequestDTO)
    case confirm(sessionId: UUID, SaveRecordRequestDTO)
    case cancel(sessionId: UUID)

    var path: String {
        switch self {
        case .create:
            "api/v1/voice-sessions"
        case let .submitTurns(sessionId, _):
            "api/v1/voice-sessions/\(sessionId.uuidString)/turns"
        case let .confirm(sessionId, _):
            "api/v1/voice-sessions/\(sessionId.uuidString)/confirm"
        case let .cancel(sessionId):
            "api/v1/voice-sessions/\(sessionId.uuidString)/cancel"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .submitTurns:
            .patch
        case .create, .confirm, .cancel:
            .post
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .submitTurns(_, dto):
            dto
        case let .confirm(_, dto):
            dto
        case .create, .cancel:
            nil
        }
    }

    var requiresAuth: Bool { true }
}
