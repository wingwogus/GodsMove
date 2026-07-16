//
//  RealtimeEventParser.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// "oai-events" 데이터 채널로 오는 OpenAI Realtime 서버 이벤트(JSON)를
/// `VoiceRealtimeEvent`로 옮기는 유일한 지점. WebRTC를 import하지 않는 순수 함수라
/// 픽스처 JSON으로 단위 테스트한다. 이벤트명이 바뀌면 여기 한 곳만 고친다.
///
/// GA 이벤트명을 기본으로 하되, 베타 시절 이름도 함께 받는다
/// (gpt-realtime GA 전환기라 어느 쪽이 오는지 첫 실연결에서 확인 전까지 방어).
enum RealtimeEventParser {
    static func parse(_ data: Data) -> VoiceRealtimeEvent? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return nil }

        switch type {
        case "conversation.item.added", "conversation.item.created":
            // message 아이템만 말풍선 슬롯이 된다. function_call 아이템은
            // response.output_item.done에서 따로 처리.
            guard let item = json["item"] as? [String: Any],
                  item["type"] as? String == "message",
                  let itemId = item["id"] as? String,
                  let role = role(from: item["role"]) else { return nil }
            return .itemStarted(itemId: itemId, role: role)

        case "conversation.item.input_audio_transcription.completed":
            guard let itemId = json["item_id"] as? String,
                  let transcript = json["transcript"] as? String else { return nil }
            return .userTranscript(itemId: itemId, text: transcript)

        case "response.output_audio_transcript.delta", "response.audio_transcript.delta":
            guard let itemId = json["item_id"] as? String,
                  let delta = json["delta"] as? String else { return nil }
            return .assistantTranscriptDelta(itemId: itemId, delta: delta)

        case "response.output_audio_transcript.done", "response.audio_transcript.done":
            guard let itemId = json["item_id"] as? String,
                  let transcript = json["transcript"] as? String else { return nil }
            return .assistantTranscriptDone(itemId: itemId, text: transcript)

        case "response.output_item.done":
            guard let item = json["item"] as? [String: Any],
                  item["type"] as? String == "function_call",
                  let name = item["name"] as? String,
                  let arguments = item["arguments"] as? String else { return nil }
            return .functionCall(name: name, argumentsJSON: arguments)

        case "response.done":
            return .responseCompleted

        case "error":
            let message = ((json["error"] as? [String: Any])?["message"] as? String) ?? "알 수 없는 오류"
            return .failed(reason: message)

        default:
            return nil
        }
    }

    private static func role(from value: Any?) -> VoiceTurnRole? {
        switch value as? String {
        case "user": .user
        case "assistant": .assistant
        default: nil
        }
    }
}
