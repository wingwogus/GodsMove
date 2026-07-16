//
//  RecordVoiceComposeViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import AVFAudio
import Foundation

/// 음성 기록 대화 화면의 상태 머신 (BR-VOICE-001~008, BR-STATE-001).
///
/// 흐름: 마이크 탭 → 권한 + 세션 발급 + Realtime 연결(`preparing`) → 대화(`conversing`,
/// 마이크 탭은 mute 토글) → save_farming_record tool 호출을 본 응답이 끝나거나 사용자가
/// 완료를 누르면 종료(`processing`: 농약 resolve + turns 1회 제출) → `reviewing`으로
/// 핸드오프. 비정상 종료는 전부 `failed`로 접고 새 세션으로만 재시작한다(BR-VOICE-008).
/// 서버가 세션을 재개할 수 없으므로 세션은 로컬에 영속화하지 않는다.
@MainActor
@Observable
final class RecordVoiceComposeViewModel {
    private(set) var phase: VoiceSessionPhase = .idle
    private(set) var transcript: [VoiceTranscriptItem] = []
    /// 검토 화면 핸드오프. `navigationDestination(item:)` 바인딩용이라 settable.
    var reviewHandoff: VoiceReviewHandoff?
    /// VOICE_002(이미 처리된 세션) — 재시도 금지. 플로우 컨테이너가 토스트와 함께 닫는다.
    private(set) var sessionInvalidated = false

    private let voiceRepository: any VoiceSessionRepository
    private let recordRepository: any RecordRepository
    private let transport: any VoiceRealtimeTransport
    private let requestMicPermission: @Sendable () async -> Bool

    private var sessionId: UUID?
    private var eventTask: Task<Void, Never>?
    /// save_farming_record tool 호출의 arguments 원문. turns의 extractedFields로도 실린다.
    private var toolArgumentsJSON: String?

    init(
        voiceRepository: any VoiceSessionRepository,
        recordRepository: any RecordRepository,
        transport: any VoiceRealtimeTransport,
        requestMicPermission: @escaping @Sendable () async -> Bool = {
            await AVAudioApplication.requestRecordPermission()
        }
    ) {
        self.voiceRepository = voiceRepository
        self.recordRepository = recordRepository
        self.transport = transport
        self.requestMicPermission = requestMicPermission
    }

    /// 완료 버튼 활성화: 대화 중이고, 사용자 발화가 잡혔거나(전사) 초안(tool 호출)이 이미 있을 때.
    /// turns가 서버에서 @NotEmpty라 빈 대화 제출을 버튼 단계에서 막는다.
    var canFinish: Bool {
        guard case .conversing = phase else { return false }
        return hasUserSpeech || toolArgumentsJSON != nil
    }

    private var hasUserSpeech: Bool {
        transcript.contains { $0.role == .user && !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    // MARK: - 사용자 입력

    func micTapped() {
        switch phase {
        case .idle:
            Task { await start() }
        case let .conversing(muted):
            phase = .conversing(muted: !muted)
            Task { await transport.setMuted(!muted) }
        case .preparing, .processing, .reviewing, .failed, .cancelled:
            break
        }
    }

    func finishTapped() {
        guard canFinish else { return }
        Task { await endConversation() }
    }

    /// failed에서만 유효. BR-VOICE-008: 기존 세션은 버리고 새 세션으로 시작한다.
    func retryTapped() {
        guard case .failed = phase else { return }
        Task { await start() }
    }

    /// 뒤로가기/닫기 (BR-VOICE-007). 서버 취소는 best-effort — 실패해도 화면은 닫힌다.
    func abandon() {
        switch phase {
        case .idle, .preparing, .conversing, .failed, .reviewing:
            eventTask?.cancel()
            eventTask = nil
            phase = .cancelled
            let sessionId = self.sessionId
            self.sessionId = nil
            Task { [transport, voiceRepository] in
                await transport.close()
                if let sessionId { await voiceRepository.cancel(sessionId: sessionId) }
            }
        case .processing, .cancelled:
            break
        }
    }

    // MARK: - 세션 시작

    private func start() async {
        transcript = []
        toolArgumentsJSON = nil
        sessionId = nil
        reviewHandoff = nil
        phase = .preparing

        guard await requestMicPermission() else {
            phase = .failed(message: "마이크 권한이 필요해요. 설정에서 마이크 접근을 허용해주세요.")
            return
        }

        do {
            let session = try await voiceRepository.createSession()
            sessionId = session.sessionId
            let stream = try await transport.connect(clientSecret: session.clientSecret)
            eventTask = Task { [weak self] in
                for await event in stream {
                    self?.handle(event)
                }
            }
        } catch {
            await failAndCleanUp(message: Self.failureMessage(for: error))
        }
    }

    // MARK: - Realtime 이벤트

    private func handle(_ event: VoiceRealtimeEvent) {
        switch phase {
        case .cancelled, .failed, .reviewing, .idle:
            return
        case .preparing, .conversing, .processing:
            break
        }

        switch event {
        case .connected:
            if phase == .preparing { phase = .conversing(muted: false) }

        case let .itemStarted(itemId, role):
            guard index(of: itemId) == nil else { return }
            transcript.append(VoiceTranscriptItem(itemId: itemId, role: role, text: ""))

        case let .userTranscript(itemId, text):
            upsert(itemId: itemId, role: .user) { $0.text = text }

        case let .assistantTranscriptDelta(itemId, delta):
            upsert(itemId: itemId, role: .assistant) { $0.text += delta }

        case let .assistantTranscriptDone(itemId, text):
            upsert(itemId: itemId, role: .assistant) { $0.text = text }

        case let .functionCall(name, argumentsJSON):
            guard name == "save_farming_record" else { return }
            toolArgumentsJSON = argumentsJSON

        case .responseCompleted:
            // 초안이 나온 응답 사이클이 닫히면 자동 종료 — 마무리 안내 음성/전사가 끝난 뒤다.
            if toolArgumentsJSON != nil, case .conversing = phase {
                Task { await endConversation() }
            }

        case let .failed(reason):
            if phase == .preparing || isConversing {
                Task { await failAndCleanUp(message: reason) }
            }

        case .closed:
            if isConversing {
                Task { await failAndCleanUp(message: "연결이 끊어졌어요. 다시 시도해주세요.") }
            }
        }
    }

    private var isConversing: Bool {
        if case .conversing = phase { true } else { false }
    }

    private func index(of itemId: String) -> Int? {
        transcript.firstIndex { $0.itemId == itemId }
    }

    /// 전사 이벤트가 itemStarted보다 먼저 오는 경우도 방어(슬롯을 만들어 붙인다).
    private func upsert(itemId: String, role: VoiceTurnRole, _ update: (inout VoiceTranscriptItem) -> Void) {
        if let index = index(of: itemId) {
            update(&transcript[index])
        } else {
            var item = VoiceTranscriptItem(itemId: itemId, role: role, text: "")
            update(&item)
            transcript.append(item)
        }
    }

    // MARK: - 대화 종료 → turns 제출 (BR: PROCESSING)

    private func endConversation() async {
        guard case .conversing = phase else { return }
        phase = .processing
        eventTask?.cancel()
        eventTask = nil
        await transport.close()

        guard let sessionId else {
            phase = .failed(message: "세션 정보를 잃어버렸어요. 다시 시도해주세요.")
            return
        }

        let candidate = toolArgumentsJSON.flatMap(VoiceCandidateMapper.parseToolArguments)
        let (pesticide, pest) = await resolvePestControl(candidate)
        let candidateDTO = VoiceCandidateMapper.makeCandidateDTO(
            from: candidate, resolvedPesticide: pesticide, resolvedPest: pest, now: Date()
        )

        do {
            let missingFields = try await voiceRepository.submitTurns(
                sessionId: sessionId, turns: buildTurns(), candidate: candidateDTO
            )
            reviewHandoff = VoiceReviewHandoff(
                sessionId: sessionId,
                prefill: VoiceCandidateMapper.makePrefill(
                    from: candidate, resolvedPesticide: pesticide, resolvedPest: pest,
                    missingFields: missingFields
                )
            )
            phase = .reviewing
        } catch VoiceSessionError.alreadyProcessed {
            sessionInvalidated = true
        } catch VoiceSessionError.providerUnavailable {
            phase = .failed(message: "연결이 원활하지 않아요. 잠시 후 다시 시도해주세요.")
        } catch {
            await cancelSessionBestEffort()
            phase = .failed(message: RecordErrorMessage.text(for: error))
        }
    }

    /// 말풍선 순서 그대로 turns를 만든다. 마지막 ASSISTANT 턴에 tool 원문을 싣고,
    /// 전사가 전혀 없으면(입력 전사 미지원 폴백) placeholder를 합성한다 — content는 @NotBlank.
    private func buildTurns() -> [VoiceTurn] {
        var turns: [VoiceTurn] = transcript.compactMap { item in
            let text = item.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { return nil }
            return VoiceTurn(role: item.role, content: text)
        }
        if toolArgumentsJSON != nil, !turns.contains(where: { $0.role == .user }) {
            turns.insert(VoiceTurn(role: .user, content: "(음성 전사를 사용할 수 없었습니다)"), at: 0)
        }
        if let toolArgumentsJSON {
            if let lastAssistant = turns.lastIndex(where: { $0.role == .assistant }) {
                let existing = turns[lastAssistant]
                turns[lastAssistant] = VoiceTurn(
                    role: .assistant, content: existing.content, extractedFields: toolArgumentsJSON
                )
            } else {
                turns.append(VoiceTurn(
                    role: .assistant, content: "영농일지 초안을 만들었어요.", extractedFields: toolArgumentsJSON
                ))
            }
        }
        if turns.isEmpty {
            // canFinish가 막지만 자동 종료 경로 방어 — turns는 서버에서 @NotEmpty.
            turns.append(VoiceTurn(role: .user, content: "(대화 내용이 기록되지 않았습니다)"))
        }
        return turns
    }

    /// 도구는 농약을 이름 문자열로 주지만 서버 candidate/confirm은 pesticideId가 필수라
    /// 카탈로그에서 resolve한다. 정확 일치 → 유일한 검색 결과 순으로 보수적으로만 매칭하고,
    /// 실패하면 검토 화면에서 사용자가 직접 고른다(BR-AI-005).
    private func resolvePestControl(_ candidate: VoiceToolCandidate?) async -> (Pesticide?, Pest?) {
        guard candidate?.workType == .pestControl,
              let name = candidate?.pestControl?.pesticideName else { return (nil, nil) }

        let matches = (try? await recordRepository.searchPesticides(keyword: name)) ?? []
        let pesticide = matches.first { $0.itemName == name } ?? (matches.count == 1 ? matches.first : nil)
        guard let pesticide else { return (nil, nil) }

        guard let target = candidate?.pestControl?.pestTarget else { return (pesticide, nil) }
        let pests = (try? await recordRepository.fetchPests(pesticideId: pesticide.id)) ?? []
        return (pesticide, pests.first { $0.name == target })
    }

    // MARK: - 실패 처리 (BR-VOICE-008)

    private func failAndCleanUp(message: String) async {
        eventTask?.cancel()
        eventTask = nil
        await transport.close()
        await cancelSessionBestEffort()
        phase = .failed(message: message)
    }

    private func cancelSessionBestEffort() async {
        guard let sessionId else { return }
        self.sessionId = nil
        await voiceRepository.cancel(sessionId: sessionId)
    }

    private static func failureMessage(for error: any Error) -> String {
        switch error {
        case VoiceSessionError.providerUnavailable:
            "연결이 원활하지 않아요. 잠시 후 다시 시도해주세요."
        case VoiceRealtimeTransportError.sdpExchangeFailed, VoiceRealtimeTransportError.connectionSetupFailed:
            "음성 연결에 실패했어요. 네트워크 상태를 확인하고 다시 시도해주세요."
        default:
            RecordErrorMessage.text(for: error)
        }
    }
}
