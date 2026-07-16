//
//  RecordVoiceComposeViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

/// 음성 세션 상태 머신(BR-VOICE-*, BR-STATE-001) 테스트. 전송은 수동 스텁으로 이벤트를
/// 한 건씩 흘리고, 상태 전이는 짧은 폴링 대기(`waitUntil`)로 관찰한다.
@MainActor
@Suite("RecordVoiceComposeViewModel 상태 머신")
struct RecordVoiceComposeViewModelTests {

    private struct Unused: Error {}

    // MARK: - 스텁

    private actor ManualVoiceTransport: VoiceRealtimeTransport {
        private var continuation: AsyncStream<VoiceRealtimeEvent>.Continuation?
        private(set) var connectCount = 0
        private(set) var lastClientSecret: String?
        private(set) var muteCalls: [Bool] = []
        private(set) var closeCount = 0

        func connect(clientSecret: String) async throws -> AsyncStream<VoiceRealtimeEvent> {
            connectCount += 1
            lastClientSecret = clientSecret
            let (stream, continuation) = AsyncStream.makeStream(of: VoiceRealtimeEvent.self)
            self.continuation = continuation
            return stream
        }

        func emit(_ event: VoiceRealtimeEvent) {
            continuation?.yield(event)
        }

        func setMuted(_ muted: Bool) {
            muteCalls.append(muted)
        }

        func close() {
            closeCount += 1
            continuation?.finish()
            continuation = nil
        }
    }

    private actor StubVoiceSessionRepository: VoiceSessionRepository {
        private let sessionInfo: VoiceSessionInfo
        private let createError: (any Error)?
        private let submitError: (any Error)?
        private let missingFields: [String]
        private(set) var createCount = 0
        private(set) var submittedTurns: [[VoiceTurn]] = []
        private(set) var submittedCandidates: [VoiceCandidateRequestDTO] = []
        private(set) var cancelledSessionIds: [UUID] = []

        init(
            sessionInfo: VoiceSessionInfo = VoiceSessionInfo(
                sessionId: UUID(), clientSecret: "ek_test", model: "gpt-realtime"
            ),
            missingFields: [String] = [],
            createError: (any Error)? = nil,
            submitError: (any Error)? = nil
        ) {
            self.sessionInfo = sessionInfo
            self.missingFields = missingFields
            self.createError = createError
            self.submitError = submitError
        }

        func createSession() async throws -> VoiceSessionInfo {
            createCount += 1
            if let createError { throw createError }
            return sessionInfo
        }

        func submitTurns(
            sessionId: UUID, turns: [VoiceTurn], candidate: VoiceCandidateRequestDTO
        ) async throws -> [String] {
            submittedTurns.append(turns)
            submittedCandidates.append(candidate)
            if let submitError { throw submitError }
            return missingFields
        }

        func confirm(sessionId: UUID, _ request: SaveRecordRequestDTO) async throws -> UUID {
            throw Unused()
        }

        func cancel(sessionId: UUID) async {
            cancelledSessionIds.append(sessionId)
        }
    }

    private struct StubRecordRepository: RecordRepository {
        var pesticides: [Pesticide] = []
        var pests: [Pest] = []

        func fetchRecords(_ query: RecordQuery) async throws -> RecordPage { throw Unused() }
        func fetchDetail(id: UUID) async throws -> RecordDetail { throw Unused() }
        func fetchCoaching(id: UUID) async throws -> RecordCoaching { throw Unused() }
        func deleteRecord(id: UUID) async throws { throw Unused() }
        func fetchActiveCrops() async throws -> [ActiveCrop] { throw Unused() }
        func fetchFarmCrops() async throws -> [FarmWithCrops] { throw Unused() }
        func searchPesticides(keyword: String?) async throws -> [Pesticide] { pesticides }
        func fetchPests(pesticideId: UUID) async throws -> [Pest] { pests }
        func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID { throw Unused() }
    }

    private func makeViewModel(
        voiceRepository: StubVoiceSessionRepository = StubVoiceSessionRepository(),
        recordRepository: StubRecordRepository = StubRecordRepository(),
        transport: ManualVoiceTransport = ManualVoiceTransport(),
        micPermission: Bool = true
    ) -> (RecordVoiceComposeViewModel, StubVoiceSessionRepository, ManualVoiceTransport) {
        let viewModel = RecordVoiceComposeViewModel(
            voiceRepository: voiceRepository,
            recordRepository: recordRepository,
            transport: transport,
            requestMicPermission: { micPermission }
        )
        return (viewModel, voiceRepository, transport)
    }

    /// 조건이 참이 될 때까지 최대 ~1초 폴링. 스텁은 스레드 홉만 하므로 실제로는 수 ms 안에 끝난다.
    @discardableResult
    private func waitUntil(_ condition: () async -> Bool) async -> Bool {
        for _ in 0..<500 {
            if await condition() { return true }
            try? await Task.sleep(for: .milliseconds(2))
        }
        return await condition()
    }

    private func isFailed(_ phase: VoiceSessionPhase, containing keyword: String) -> Bool {
        if case let .failed(message) = phase { message.contains(keyword) } else { false }
    }

    /// conversing까지 진입시키는 공통 준비 단계.
    private func startConversation(
        _ vm: RecordVoiceComposeViewModel, _ transport: ManualVoiceTransport
    ) async {
        vm.micTapped()
        await waitUntil { await transport.connectCount == 1 }
        await transport.emit(.connected)
        await waitUntil { vm.phase == .conversing(muted: false) }
    }

    private static let toolJSON = """
    {"workType":"WATERING","memo":"상추에 물 줬음","watering":{"irrigationAmount":"NORMAL","irrigationMethod":"DRIP"}}
    """

    // MARK: - 시작

    @Test("마이크 탭: idle → preparing → (connected) → conversing, 발급된 clientSecret으로 연결")
    func happyPathStart() async {
        let (vm, repo, transport) = makeViewModel()

        vm.micTapped()
        #expect(await waitUntil { await transport.connectCount == 1 })
        #expect(vm.phase == .preparing) // connected 전에는 대화로 넘어가지 않는다
        #expect(await repo.createCount == 1)
        #expect(await transport.lastClientSecret == "ek_test")

        await transport.emit(.connected)
        #expect(await waitUntil { vm.phase == .conversing(muted: false) })
    }

    @Test("마이크 권한 거부: failed로 접히고 세션은 만들지 않는다")
    func micPermissionDenied() async {
        let (vm, repo, _) = makeViewModel(micPermission: false)

        vm.micTapped()
        #expect(await waitUntil { self.isFailed(vm.phase, containing: "마이크 권한") })
        #expect(await repo.createCount == 0)
    }

    @Test("세션 발급 503(VOICE_003): 잠시 후 재시도 안내로 실패한다")
    func providerUnavailableOnCreate() async {
        let repo = StubVoiceSessionRepository(createError: VoiceSessionError.providerUnavailable)
        let (vm, _, _) = makeViewModel(voiceRepository: repo)

        vm.micTapped()
        #expect(await waitUntil { self.isFailed(vm.phase, containing: "잠시 후") })
    }

    // MARK: - 대화 → 제출

    @Test("전체 흐름: tool 호출 응답이 끝나면 자동 종료돼 turns 순서·초안 원문·missingFields가 전달된다")
    func fullFlowToReviewing() async {
        let repo = StubVoiceSessionRepository(missingFields: ["farmId", "cropId"])
        let (vm, _, transport) = makeViewModel(voiceRepository: repo)
        await startConversation(vm, transport)

        await transport.emit(.itemStarted(itemId: "u1", role: .user))
        await transport.emit(.userTranscript(itemId: "u1", text: "상추에 물 줬어요"))
        await transport.emit(.itemStarted(itemId: "a1", role: .assistant))
        await transport.emit(.assistantTranscriptDone(itemId: "a1", text: "기록해둘게요!"))
        await transport.emit(.functionCall(name: "save_farming_record", argumentsJSON: Self.toolJSON))
        await transport.emit(.responseCompleted)

        #expect(await waitUntil { vm.phase == .reviewing })

        let turns = await repo.submittedTurns.first
        #expect(turns?.count == 2)
        #expect(turns?.first == VoiceTurn(role: .user, content: "상추에 물 줬어요"))
        #expect(turns?.last?.role == .assistant)
        #expect(turns?.last?.extractedFields == Self.toolJSON)

        let candidate = await repo.submittedCandidates.first
        #expect(candidate?.workType == "WATERING")
        #expect(candidate?.watering?.irrigationMethod == "DRIP")

        #expect(vm.reviewHandoff?.prefill.missingFields == ["farmId", "cropId"])
        #expect(vm.reviewHandoff?.prefill.workType == .watering)
        #expect(await transport.closeCount >= 1)
    }

    @Test("늦게 도착한 사용자 전사도 itemStarted 순서를 유지한다")
    func lateUserTranscriptKeepsOrder() async {
        let repo = StubVoiceSessionRepository()
        let (vm, _, transport) = makeViewModel(voiceRepository: repo)
        await startConversation(vm, transport)

        await transport.emit(.itemStarted(itemId: "u1", role: .user))
        await transport.emit(.itemStarted(itemId: "a1", role: .assistant))
        await transport.emit(.assistantTranscriptDone(itemId: "a1", text: "어떤 작업을 하셨나요?"))
        // 사용자 전사가 assistant 답변보다 늦게 완료되는 실제 시나리오
        await transport.emit(.userTranscript(itemId: "u1", text: "물 줬어요"))
        await transport.emit(.functionCall(name: "save_farming_record", argumentsJSON: Self.toolJSON))
        await transport.emit(.responseCompleted)

        #expect(await waitUntil { vm.phase == .reviewing })
        let turns = await repo.submittedTurns.first
        #expect(turns?.map(\.role) == [.user, .assistant])
    }

    @Test("완료 버튼: 사용자 발화 전에는 비활성, 전사가 잡히면 활성 → 수동 종료 제출")
    func manualFinishGating() async {
        let repo = StubVoiceSessionRepository()
        let (vm, _, transport) = makeViewModel(voiceRepository: repo)
        await startConversation(vm, transport)
        #expect(!vm.canFinish)

        await transport.emit(.itemStarted(itemId: "u1", role: .user))
        #expect(await waitUntil { vm.transcript.count == 1 })
        #expect(!vm.canFinish) // 빈 텍스트 슬롯만으로는 비활성

        await transport.emit(.userTranscript(itemId: "u1", text: "밭 갈았어요"))
        #expect(await waitUntil { vm.canFinish })

        vm.finishTapped()
        #expect(await waitUntil { vm.phase == .reviewing })
        // tool 호출 없이 수동 종료 → 후보는 비었고 workedAt만 기본값
        let candidate = await repo.submittedCandidates.first
        #expect(candidate?.workType == nil)
        #expect(candidate?.workedAt != nil)
    }

    @Test("마이크 재탭은 mute 토글이지 종료가 아니다")
    func micToggleMutes() async {
        let (vm, _, transport) = makeViewModel()
        await startConversation(vm, transport)

        vm.micTapped()
        #expect(vm.phase == .conversing(muted: true))
        #expect(await waitUntil { await transport.muteCalls == [true] })

        vm.micTapped()
        #expect(vm.phase == .conversing(muted: false))
        #expect(await waitUntil { await transport.muteCalls == [true, false] })
        #expect(await transport.closeCount == 0)
    }

    // MARK: - 실패/취소 (BR-VOICE-007/008)

    @Test("대화 중 전송 실패: failed로 접고 세션을 best-effort 취소, turns는 제출하지 않는다")
    func transportFailureDuringConversation() async {
        let repo = StubVoiceSessionRepository()
        let (vm, _, transport) = makeViewModel(voiceRepository: repo)
        await startConversation(vm, transport)

        await transport.emit(.failed(reason: "네트워크 연결에 실패했어요."))
        #expect(await waitUntil { self.isFailed(vm.phase, containing: "네트워크") })
        #expect(await repo.cancelledSessionIds.count == 1)
        #expect(await repo.submittedTurns.isEmpty)
    }

    @Test("재시도는 항상 새 세션이다 (BR-VOICE-008)")
    func retryCreatesNewSession() async {
        let repo = StubVoiceSessionRepository()
        let (vm, _, transport) = makeViewModel(voiceRepository: repo)
        await startConversation(vm, transport)

        await transport.emit(.itemStarted(itemId: "u1", role: .user))
        await transport.emit(.userTranscript(itemId: "u1", text: "물 줬어요"))
        await waitUntil { vm.transcript.count == 1 }
        await transport.emit(.failed(reason: "끊김"))
        #expect(await waitUntil { self.isFailed(vm.phase, containing: "끊김") })

        vm.retryTapped()
        #expect(await waitUntil { await repo.createCount == 2 })
        #expect(vm.transcript.isEmpty) // 새 세션은 대화도 초기화
        await transport.emit(.connected)
        #expect(await waitUntil { vm.phase == .conversing(muted: false) })
    }

    @Test("turns 제출 중 VOICE_002: 재시도 없이 '이미 처리됨'으로 플로우를 끝낸다")
    func alreadyProcessedOnSubmit() async {
        let repo = StubVoiceSessionRepository(submitError: VoiceSessionError.alreadyProcessed)
        let (vm, _, transport) = makeViewModel(voiceRepository: repo)
        await startConversation(vm, transport)

        await transport.emit(.itemStarted(itemId: "u1", role: .user))
        await transport.emit(.userTranscript(itemId: "u1", text: "물 줬어요"))
        #expect(await waitUntil { vm.canFinish })

        vm.finishTapped()
        #expect(await waitUntil { vm.sessionInvalidated })
        #expect(await repo.submittedTurns.count == 1) // 재시도 금지
        #expect(vm.reviewHandoff == nil)
    }

    @Test("뒤로가기: cancelled로 접고 서버 취소는 best-effort, 이후 이벤트는 무시한다")
    func abandonIgnoresLaterEvents() async {
        let repo = StubVoiceSessionRepository()
        let (vm, _, transport) = makeViewModel(voiceRepository: repo)
        await startConversation(vm, transport)

        vm.abandon()
        #expect(vm.phase == .cancelled)
        #expect(await waitUntil { await repo.cancelledSessionIds.count == 1 })

        await transport.emit(.itemStarted(itemId: "u1", role: .user))
        try? await Task.sleep(for: .milliseconds(50)) // 이벤트 전파 시간
        #expect(vm.transcript.isEmpty)
        #expect(vm.phase == .cancelled)
    }
}
