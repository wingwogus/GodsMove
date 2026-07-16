//
//  VoiceSessionEndpointTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("VoiceSessionEndpoint")
struct VoiceSessionEndpointTests {

    @Test("경로와 HTTP 메서드가 배포 계약과 일치한다")
    func pathsAndMethods() {
        let sessionId = UUID()
        let turns = SubmitTurnsRequestDTO(turns: [], candidate: Self.emptyCandidate)

        #expect(VoiceSessionEndpoint.create.path == "api/v1/voice-sessions")
        #expect(VoiceSessionEndpoint.create.method == .post)

        let submit = VoiceSessionEndpoint.submitTurns(sessionId: sessionId, turns)
        #expect(submit.path == "api/v1/voice-sessions/\(sessionId.uuidString)/turns")
        #expect(submit.method == .patch)

        let confirm = VoiceSessionEndpoint.confirm(sessionId: sessionId, Self.saveRequest)
        #expect(confirm.path == "api/v1/voice-sessions/\(sessionId.uuidString)/confirm")
        #expect(confirm.method == .post)

        let cancel = VoiceSessionEndpoint.cancel(sessionId: sessionId)
        #expect(cancel.path == "api/v1/voice-sessions/\(sessionId.uuidString)/cancel")
        #expect(cancel.method == .post)
    }

    @Test("모든 음성 세션 엔드포인트는 인증이 필요하다")
    func requiresAuth() {
        #expect(VoiceSessionEndpoint.create.requiresAuth)
        #expect(VoiceSessionEndpoint.cancel(sessionId: UUID()).requiresAuth)
    }

    @Test("쓰기 엔드포인트만 바디를 갖는다 — create/cancel은 바디 없는 POST")
    func bodyPresence() {
        let sessionId = UUID()
        #expect(VoiceSessionEndpoint.create.body == nil)
        #expect(VoiceSessionEndpoint.cancel(sessionId: sessionId).body == nil)
        #expect(VoiceSessionEndpoint.submitTurns(
            sessionId: sessionId,
            SubmitTurnsRequestDTO(turns: [], candidate: Self.emptyCandidate)
        ).body != nil)
        #expect(VoiceSessionEndpoint.confirm(sessionId: sessionId, Self.saveRequest).body != nil)
    }

    @Test("turns 요청 JSON: role은 대문자, nil 필드는 키 자체가 빠진다")
    func submitTurnsJSONShape() throws {
        let request = SubmitTurnsRequestDTO(
            turns: [
                VoiceTurnRequestDTO(role: "USER", content: "물 줬어요", extractedFields: nil),
                VoiceTurnRequestDTO(role: "ASSISTANT", content: "확인했어요", extractedFields: "{\"workType\":\"WATERING\"}"),
            ],
            candidate: VoiceCandidateRequestDTO(
                farmId: nil, cropId: nil, workType: "WATERING", workedAt: nil, memo: "메모",
                planting: nil, watering: nil, fertilizing: nil, pestControl: nil, weeding: nil, harvest: nil
            )
        )
        let data = try JSONEncoder().encode(request)
        let json = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])

        let turns = try #require(json["turns"] as? [[String: Any]])
        #expect(turns.count == 2)
        #expect(turns[0]["role"] as? String == "USER")
        #expect(turns[0]["extractedFields"] == nil)
        #expect(turns[1]["role"] as? String == "ASSISTANT")
        #expect(turns[1]["extractedFields"] as? String == "{\"workType\":\"WATERING\"}")

        let candidate = try #require(json["candidate"] as? [String: Any])
        #expect(candidate["workType"] as? String == "WATERING")
        #expect(candidate["memo"] as? String == "메모")
        #expect(candidate["farmId"] == nil)
        #expect(candidate["pestControl"] == nil)
    }

    // MARK: - Fixtures

    private static let emptyCandidate = VoiceCandidateRequestDTO(
        farmId: nil, cropId: nil, workType: nil, workedAt: nil, memo: nil,
        planting: nil, watering: nil, fertilizing: nil, pestControl: nil, weeding: nil, harvest: nil
    )

    private static let saveRequest = SaveRecordRequestDTO(
        farmId: UUID(),
        cropId: UUID(),
        workType: "WATERING",
        workedAt: "2026-07-16T10:00:00",
        weatherCondition: "맑음",
        weatherTemperature: 27,
        memo: String(repeating: "물주기 작업 메모. ", count: 4),
        planting: nil,
        watering: WateringDetailRequestDTO(irrigationAmount: "NORMAL", irrigationMethod: "DRIP"),
        fertilizing: nil,
        pestControl: nil,
        weeding: nil,
        harvest: nil,
        mediaIds: [],
        entryMode: "VOICE"
    )
}
