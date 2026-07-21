//
//  WebRTCVoiceTransport.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import AVFAudio
import Foundation
import os
@preconcurrency import WebRTC

/// OpenAI Realtime과의 WebRTC 직결 구현. 오디오/이벤트는 백엔드를 거치지 않는다.
///
/// 연결 절차(백엔드 연동 가이드 그대로):
/// 1. 오디오 세션을 통화(voiceChat, 스피커 기본) 모드로 구성
/// 2. RTCPeerConnection 생성 + 마이크 트랙 추가 + "oai-events" 데이터 채널 생성
/// 3. SDP offer를 `POST /v1/realtime/calls`(Bearer clientSecret)로 보내 answer 수신
/// 4. 채널 open 시 입력 전사 활성화 session.update 1회 전송 후 `.connected` 방출
///
/// Swift 6 격리: RTC 객체는 전부 이 actor 안에서만 만들고 만진다. WebRTC가 자기
/// 스레드에서 부르는 델리게이트는 `VoiceRealtimeEventRelay`로 분리하고, relay는
/// Sendable한 저장 프로퍼티(스트림 continuation, 페이로드 Data)만 가진 순수 전달자라
/// 데이터 경합이 구조적으로 없다.
actor WebRTCVoiceTransport: VoiceRealtimeTransport {

    /// libWebRTC 전역 초기화는 프로세스당 1회.
    private static let sslInitialized: Bool = {
        RTCInitializeSSL()
        return true
    }()

    /// factory는 non-Sendable이라 actor 인스턴스 소유로 둔다(전역 static 불가).
    private let factory: RTCPeerConnectionFactory

    init() {
        _ = Self.sslInitialized
        factory = RTCPeerConnectionFactory()
    }

    private var peerConnection: RTCPeerConnection?
    private var audioTrack: RTCAudioTrack?
    private var dataChannel: RTCDataChannel?
    private var relay: VoiceRealtimeEventRelay?
    private var continuation: AsyncStream<VoiceRealtimeEvent>.Continuation?

    func connect(clientSecret: String) async throws -> AsyncStream<VoiceRealtimeEvent> {
        close()
        try configureAudioSession()

        let (stream, continuation) = AsyncStream.makeStream(of: VoiceRealtimeEvent.self)
        let relay = VoiceRealtimeEventRelay(
            continuation: continuation,
            onChannelOpenPayload: Self.transcriptionSessionUpdatePayload()
        )

        let config = RTCConfiguration()
        config.sdpSemantics = .unifiedPlan
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        guard let peerConnection = factory.peerConnection(
            with: config, constraints: constraints, delegate: relay
        ) else {
            throw VoiceRealtimeTransportError.connectionSetupFailed
        }

        let audioSource = factory.audioSource(with: constraints)
        let audioTrack = factory.audioTrack(with: audioSource, trackId: "mic0")
        _ = peerConnection.add(audioTrack, streamIds: ["s0"])

        guard let dataChannel = peerConnection.dataChannel(
            forLabel: "oai-events", configuration: RTCDataChannelConfiguration()
        ) else {
            peerConnection.close()
            throw VoiceRealtimeTransportError.connectionSetupFailed
        }
        dataChannel.delegate = relay

        self.peerConnection = peerConnection
        self.audioTrack = audioTrack
        self.dataChannel = dataChannel
        self.relay = relay
        self.continuation = continuation

        do {
            let offerSDP = try await makeOffer(peerConnection, constraints: constraints)
            try await setLocalDescription(peerConnection, type: .offer, sdp: offerSDP)
            // OpenAI 서버는 공인 주소라 클라이언트 후보를 SDP에 다 싣지 않아도
            // peer-reflexive로 연결된다 — ICE gathering 완료를 기다리지 않는다.
            let answerSDP = try await exchangeSDP(offerSDP: offerSDP, clientSecret: clientSecret)
            try await setRemoteDescription(peerConnection, type: .answer, sdp: answerSDP)
        } catch {
            close()
            throw error
        }

        return stream
    }

    func setMuted(_ muted: Bool) {
        audioTrack?.isEnabled = !muted
    }

    func close() {
        dataChannel?.delegate = nil
        dataChannel?.close()
        peerConnection?.close()
        continuation?.finish()
        dataChannel = nil
        audioTrack = nil
        peerConnection = nil
        relay = nil
        continuation = nil
    }

    // MARK: - 연결 단계

    /// 통화형 오디오 라우팅: 에코 제거(voiceChat) + 스피커 기본 출력.
    private func configureAudioSession() throws {
        let config = RTCAudioSessionConfiguration.webRTC()
        config.category = AVAudioSession.Category.playAndRecord.rawValue
        config.mode = AVAudioSession.Mode.voiceChat.rawValue
        config.categoryOptions = [.defaultToSpeaker, .allowBluetoothHFP]
        let session = RTCAudioSession.sharedInstance()
        session.lockForConfiguration()
        defer { session.unlockForConfiguration() }
        try session.setConfiguration(config)
    }

    /// RTCSessionDescription은 Sendable이 아니라 콜백 경계에서는 SDP 문자열만 넘긴다.
    private func makeOffer(
        _ peerConnection: RTCPeerConnection,
        constraints: RTCMediaConstraints
    ) async throws -> String {
        try await withCheckedThrowingContinuation { cont in
            peerConnection.offer(for: constraints) { sdp, error in
                if let sdp {
                    cont.resume(returning: sdp.sdp)
                } else {
                    cont.resume(throwing: error ?? VoiceRealtimeTransportError.connectionSetupFailed)
                }
            }
        }
    }

    private func setLocalDescription(
        _ peerConnection: RTCPeerConnection, type: RTCSdpType, sdp: String
    ) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            peerConnection.setLocalDescription(RTCSessionDescription(type: type, sdp: sdp)) { error in
                if let error { cont.resume(throwing: error) } else { cont.resume() }
            }
        }
    }

    private func setRemoteDescription(
        _ peerConnection: RTCPeerConnection, type: RTCSdpType, sdp: String
    ) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            peerConnection.setRemoteDescription(RTCSessionDescription(type: type, sdp: sdp)) { error in
                if let error { cont.resume(throwing: error) } else { cont.resume() }
            }
        }
    }

    /// `APIClient`를 쓰지 않는 이유: 호스트/인증/콘텐츠 타입 전부 백엔드 API와 다르고
    /// (application/sdp, 토큰 리프레시 무관), 응답도 JSON 봉투가 아닌 SDP 원문이다.
    private func exchangeSDP(offerSDP: String, clientSecret: String) async throws -> String {
        var request = URLRequest(url: Self.callsURL)
        request.httpMethod = "POST"
        request.setValue("Bearer \(clientSecret)", forHTTPHeaderField: "Authorization")
        request.setValue("application/sdp", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data(offerSDP.utf8)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode),
              let answer = String(data: data, encoding: .utf8),
              !answer.isEmpty else {
            throw VoiceRealtimeTransportError.sdpExchangeFailed
        }
        return answer
    }

    private static let callsURL = URL(string: "https://api.openai.com/v1/realtime/calls")!

    /// 백엔드 세션 발급이 입력 전사를 켜지 않아(OpenAiRealtimeSessionProvider 참고)
    /// 클라이언트가 켠다. instructions/tools는 건드리지 않는 부분 업데이트라 안전하다.
    /// 이 업데이트가 거부되면 사용자 말풍선 없이 진행된다(뷰모델이 placeholder로 폴백).
    private static func transcriptionSessionUpdatePayload() -> Data {
        let payload: [String: Any] = [
            "type": "session.update",
            "session": [
                "type": "realtime",
                "audio": [
                    "input": [
                        "transcription": ["model": "gpt-4o-mini-transcribe", "language": "ko"]
                    ]
                ],
            ],
        ]
        return (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
    }
}

/// WebRTC가 자기 스레드에서 호출하는 델리게이트 전달자. 저장 상태는 Sendable한
/// continuation과 open 시 1회 보낼 페이로드뿐이며, 모든 콜백은 parse → yield 순수 전달이다.
final class VoiceRealtimeEventRelay: NSObject {
    fileprivate static let diagnosticsLog = Logger(subsystem: "ChamChamCham", category: "voice.realtime")

    private let continuation: AsyncStream<VoiceRealtimeEvent>.Continuation
    private let onChannelOpenPayload: Data

    init(continuation: AsyncStream<VoiceRealtimeEvent>.Continuation, onChannelOpenPayload: Data) {
        self.continuation = continuation
        self.onChannelOpenPayload = onChannelOpenPayload
    }
}

extension VoiceRealtimeEventRelay: RTCDataChannelDelegate {
    func dataChannelDidChangeState(_ dataChannel: RTCDataChannel) {
        switch dataChannel.readyState {
        case .open:
            dataChannel.sendData(RTCDataBuffer(data: onChannelOpenPayload, isBinary: false))
            continuation.yield(.connected)
        case .closed:
            continuation.yield(.closed)
        case .connecting, .closing:
            break
        @unknown default:
            break
        }
    }

    func dataChannel(_ dataChannel: RTCDataChannel, didReceiveMessageWith buffer: RTCDataBuffer) {
        let event = buffer.isBinary ? nil : RealtimeEventParser.parse(buffer.data)
        logIncomingEvent(buffer, parsed: event != nil)
        guard let event else { return }
        continuation.yield(event)
    }

    /// [진단] "음성 → 작성 화면 전 필드 공백" 버그 추적용. 파서가 nil을 반환해 조용히 폐기되는
    /// 이벤트까지 포함해 실제로 어떤 oai-event가 오는지, tool 인자가 잡히는지 확인한다.
    /// 개인정보 보호를 위해 인자 값은 남기지 않고 필드명(키)·길이만 기록한다. 원인 확정 후 제거/게이팅.
    private func logIncomingEvent(_ buffer: RTCDataBuffer, parsed: Bool) {
        guard !buffer.isBinary,
              let json = try? JSONSerialization.jsonObject(with: buffer.data) as? [String: Any],
              let type = json["type"] as? String else { return }

        guard type.contains("function_call") || type.contains("output_item") else {
            Self.diagnosticsLog.debug("oai-event type=\(type, privacy: .public) parsed=\(parsed, privacy: .public)")
            return
        }

        let item = json["item"] as? [String: Any]
        let argsString = (json["arguments"] as? String) ?? (item?["arguments"] as? String)
        let name = (json["name"] as? String) ?? (item?["name"] as? String)
        let argKeys = argsString
            .flatMap { $0.data(using: .utf8) }
            .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
            .map { $0.keys.sorted().joined(separator: ",") }
        Self.diagnosticsLog.debug("""
        oai-event type=\(type, privacy: .public) parsed=\(parsed, privacy: .public) \
        name=\(name ?? "nil", privacy: .public) argsLen=\(argsString?.count ?? -1, privacy: .public) \
        argKeys=[\(argKeys ?? "nil", privacy: .public)]
        """)
    }
}

extension VoiceRealtimeEventRelay: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        switch newState {
        case .failed:
            continuation.yield(.failed(reason: "네트워크 연결에 실패했어요."))
        case .closed:
            continuation.yield(.closed)
        case .disconnected:
            // 일시적 네트워크 플랩은 ICE가 자체 복구를 시도한다. failed에서만 종료.
            break
        case .new, .checking, .connected, .completed, .count:
            break
        @unknown default:
            break
        }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}
