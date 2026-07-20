//
//  RecordSaver.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// 기록 작성 폼(RecordComposeViewModel)의 제출 대상 전략. 기본은 텍스트 작성
/// (`POST /farming-records`)이고, 음성 검토는 같은 폼을 재사용하면서 저장만
/// voice confirm 엔드포인트로 분기한다. 폼 자체는 어느 경로로 저장되는지 모른다.
protocol RecordSaver: Sendable {
    var entryMode: EntryMode { get }
    func save(_ request: SaveRecordRequestDTO) async throws -> UUID
}

struct CreateRecordSaver: RecordSaver {
    let repository: any RecordRepository

    var entryMode: EntryMode { .manual }

    func save(_ request: SaveRecordRequestDTO) async throws -> UUID {
        try await repository.createRecord(request)
    }
}

/// 음성 세션 검토 저장 (BR-VOICE-006). entryMode는 서버가 VOICE로 강제하지만
/// 계약상 필수 필드라 명시해 보낸다.
struct VoiceConfirmRecordSaver: RecordSaver {
    let repository: any VoiceSessionRepository
    let sessionId: UUID

    var entryMode: EntryMode { .voice }

    func save(_ request: SaveRecordRequestDTO) async throws -> UUID {
        try await repository.confirm(sessionId: sessionId, request)
    }
}

/// 기록 수정 저장. `PATCH /farming-records/{id}`로 라우팅한다. entryMode는 백엔드 update 커맨드가
/// 필드 자체를 갖지 않아 완전히 무시하므로 값 선택 고민 없이 `.manual`로 고정한다.
struct UpdateRecordSaver: RecordSaver {
    let repository: any RecordRepository
    let recordId: UUID

    var entryMode: EntryMode { .manual }

    func save(_ request: SaveRecordRequestDTO) async throws -> UUID {
        try await repository.updateRecord(id: recordId, request)
    }
}
