//
//  VoiceRealtimeTransport.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// OpenAI Realtime 연결에서 화면(뷰모델)이 소비하는 이벤트.
/// 뷰모델 상태 머신이 이 enum만 알도록 해서 WebRTC 없이 스텁으로 테스트한다.
enum VoiceRealtimeEvent: Sendable, Equatable {
    /// 데이터 채널 open — 대화 시작 가능.
    case connected
    /// 대화 아이템 슬롯 생성. 사용자 전사는 비동기로 늦게 오므로,
    /// 이 시점의 순서로 말풍선/turns 순서를 고정한다.
    case itemStarted(itemId: String, role: VoiceTurnRole)
    /// 사용자 발화 전사 완료.
    case userTranscript(itemId: String, text: String)
    /// AI 응답 전사 스트리밍 조각.
    case assistantTranscriptDelta(itemId: String, delta: String)
    /// AI 응답 전사 확정.
    case assistantTranscriptDone(itemId: String, text: String)
    /// 모델이 도구를 호출 — save_farming_record의 arguments 원문 JSON.
    case functionCall(name: String, argumentsJSON: String)
    /// 한 응답 사이클 종료. functionCall을 본 응답이면 여기서 대화를 끝낸다.
    case responseCompleted
    case failed(reason: String)
    case closed
}

enum VoiceRealtimeTransportError: Error {
    case connectionSetupFailed
    case sdpExchangeFailed
}

/// 클라이언트 ↔ OpenAI Realtime 직결 전송. 구현은 `WebRTCVoiceTransport` 하나지만,
/// 뷰모델 테스트가 스크립트된 이벤트를 흘릴 수 있도록 프로토콜로 분리한다.
protocol VoiceRealtimeTransport: Sendable {
    /// 백엔드가 발급한 단기 clientSecret으로 연결한다. instructions/tools/model은
    /// 세션 발급 시점에 이미 OpenAI 서버에 등록돼 있어 여기서는 토큰만 필요하다.
    func connect(clientSecret: String) async throws -> AsyncStream<VoiceRealtimeEvent>

    /// 마이크 mute 토글. 연결 종료가 아니다.
    func setMuted(_ muted: Bool) async

    func close() async
}
