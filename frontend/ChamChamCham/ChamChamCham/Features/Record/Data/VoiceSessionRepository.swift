//
//  VoiceSessionRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// 음성 세션 프레젠테이션 레이어의 단일 진입점. `RecordRepository`와 같은 구조로,
/// VOICE_00x 비즈니스 에러 코드를 `VoiceSessionError`로 접어 화면이 코드 문자열을
/// 몰라도 되게 한다.
protocol VoiceSessionRepository: Sendable {
    func createSession() async throws -> VoiceSessionInfo

    /// 대화 종료 후 1회 호출. 성공 시 서버가 WAITING_CONFIRMATION으로 전이하고
    /// 누락 필드 목록(`farmId`/`cropId`/`workType`/`workedAt`/`detail` 리터럴)을 돌려준다.
    func submitTurns(
        sessionId: UUID,
        turns: [VoiceTurn],
        candidate: VoiceCandidateRequestDTO
    ) async throws -> [String]

    @discardableResult
    func confirm(sessionId: UUID, _ request: SaveRecordRequestDTO) async throws -> UUID

    /// Best-effort 취소(BR-VOICE-007). 실패해도 사용자 플로우를 막지 않으므로 던지지 않는다.
    func cancel(sessionId: UUID) async
}

struct RemoteVoiceSessionRepository: VoiceSessionRepository {
    let apiClient: APIClient

    func createSession() async throws -> VoiceSessionInfo {
        do {
            let dto: VoiceSessionCreatedResponseDTO = try await apiClient.send(VoiceSessionEndpoint.create)
            return dto.toDomain()
        } catch {
            throw Self.foldVoiceError(error)
        }
    }

    func submitTurns(
        sessionId: UUID,
        turns: [VoiceTurn],
        candidate: VoiceCandidateRequestDTO
    ) async throws -> [String] {
        let request = SubmitTurnsRequestDTO(
            turns: turns.map {
                VoiceTurnRequestDTO(role: $0.role.rawValue, content: $0.content, extractedFields: $0.extractedFields)
            },
            candidate: candidate
        )
        do {
            let dto: VoiceProcessedResponseDTO = try await apiClient.send(
                VoiceSessionEndpoint.submitTurns(sessionId: sessionId, request)
            )
            return dto.missingFields
        } catch {
            throw Self.foldVoiceError(error)
        }
    }

    @discardableResult
    func confirm(sessionId: UUID, _ request: SaveRecordRequestDTO) async throws -> UUID {
        do {
            let dto: VoiceConfirmedResponseDTO = try await apiClient.send(
                VoiceSessionEndpoint.confirm(sessionId: sessionId, request)
            )
            return dto.recordId
        } catch {
            throw Self.foldVoiceError(error)
        }
    }

    func cancel(sessionId: UUID) async {
        _ = try? await apiClient.send(VoiceSessionEndpoint.cancel(sessionId: sessionId))
            as VoiceCancelledResponseDTO
    }

    /// 연동 가이드의 에러 계약: VOICE_002(409)는 재시도 금지 신호라 호출부가 반드시
    /// 구분할 수 있어야 한다. 그 외 에러는 그대로 통과시켜 기존 `RecordErrorMessage`가 처리.
    private static func foldVoiceError(_ error: any Error) -> any Error {
        guard case let APIError.apiError(code, _) = error else { return error }
        switch code {
        case "VOICE_001": return VoiceSessionError.notFound
        case "VOICE_002": return VoiceSessionError.alreadyProcessed
        case "VOICE_003": return VoiceSessionError.providerUnavailable
        default: return error
        }
    }
}
