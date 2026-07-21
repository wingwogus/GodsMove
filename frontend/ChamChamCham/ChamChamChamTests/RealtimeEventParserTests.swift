//
//  RealtimeEventParserTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("RealtimeEventParser")
struct RealtimeEventParserTests {

    private func parse(_ json: String) -> VoiceRealtimeEvent? {
        RealtimeEventParser.parse(Data(json.utf8))
    }

    @Test("message 아이템 추가는 역할별 itemStarted가 된다 (GA/베타 이벤트명 모두)")
    func itemStarted() {
        let ga = """
        {"type":"conversation.item.added","item":{"id":"item_1","type":"message","role":"user"}}
        """
        #expect(parse(ga) == .itemStarted(itemId: "item_1", role: .user))

        let beta = """
        {"type":"conversation.item.created","item":{"id":"item_2","type":"message","role":"assistant"}}
        """
        #expect(parse(beta) == .itemStarted(itemId: "item_2", role: .assistant))
    }

    @Test("function_call 아이템 추가는 말풍선 슬롯이 되지 않는다")
    func functionCallItemIsNotBubble() {
        let json = """
        {"type":"conversation.item.added","item":{"id":"item_3","type":"function_call","name":"save_farming_record"}}
        """
        #expect(parse(json) == nil)
    }

    @Test("사용자 전사 완료 이벤트")
    func userTranscript() {
        let json = """
        {"type":"conversation.item.input_audio_transcription.completed","item_id":"item_1","transcript":"어제 상추에 물 줬어요"}
        """
        #expect(parse(json) == .userTranscript(itemId: "item_1", text: "어제 상추에 물 줬어요"))
    }

    @Test("AI 전사 delta/done (GA/베타 이벤트명 모두)")
    func assistantTranscript() {
        #expect(parse("""
        {"type":"response.output_audio_transcript.delta","item_id":"item_2","delta":"어떤 "}
        """) == .assistantTranscriptDelta(itemId: "item_2", delta: "어떤 "))

        #expect(parse("""
        {"type":"response.audio_transcript.delta","item_id":"item_2","delta":"작물"}
        """) == .assistantTranscriptDelta(itemId: "item_2", delta: "작물"))

        #expect(parse("""
        {"type":"response.output_audio_transcript.done","item_id":"item_2","transcript":"어떤 작물인가요?"}
        """) == .assistantTranscriptDone(itemId: "item_2", text: "어떤 작물인가요?"))

        #expect(parse("""
        {"type":"response.audio_transcript.done","item_id":"item_2","transcript":"어떤 작물인가요?"}
        """) == .assistantTranscriptDone(itemId: "item_2", text: "어떤 작물인가요?"))
    }

    @Test("도구 호출은 arguments 원문 JSON을 그대로 담는다")
    func functionCall() {
        let json = """
        {"type":"response.output_item.done","item":{"id":"item_4","type":"function_call","name":"save_farming_record","arguments":"{\\"workType\\":\\"WATERING\\"}"}}
        """
        #expect(parse(json) == .functionCall(name: "save_farming_record", argumentsJSON: "{\"workType\":\"WATERING\"}"))
    }

    @Test("function_call_arguments.done의 최상위 name/arguments를 캡처한다")
    func functionCallArgumentsDone() {
        let json = """
        {"type":"response.function_call_arguments.done","name":"save_farming_record","arguments":"{\\"workType\\":\\"WATERING\\"}"}
        """
        #expect(parse(json) == .functionCall(name: "save_farming_record", argumentsJSON: "{\"workType\":\"WATERING\"}"))
    }

    @Test("빈 arguments는 유실 신호이므로 functionCall을 만들지 않는다")
    func emptyArgumentsIgnored() {
        #expect(parse(#"{"type":"response.function_call_arguments.done","name":"save_farming_record","arguments":""}"#) == nil)
        #expect(parse(#"{"type":"response.output_item.done","item":{"id":"i","type":"function_call","name":"save_farming_record","arguments":""}}"#) == nil)
    }

    @Test("message 아이템의 output_item.done은 무시한다")
    func messageOutputItemDoneIgnored() {
        let json = """
        {"type":"response.output_item.done","item":{"id":"item_5","type":"message","role":"assistant"}}
        """
        #expect(parse(json) == nil)
    }

    @Test("response.done과 error 이벤트")
    func responseDoneAndError() {
        #expect(parse(#"{"type":"response.done","response":{"id":"resp_1"}}"#) == .responseCompleted)
        #expect(parse(#"{"type":"error","error":{"message":"session expired"}}"#)
            == .failed(reason: "session expired"))
        #expect(parse(#"{"type":"error"}"#) == .failed(reason: "알 수 없는 오류"))
    }

    @Test("모르는 이벤트·비JSON·필드 누락은 nil (크래시 금지)")
    func junkInput() {
        #expect(parse(#"{"type":"session.updated","session":{}}"#) == nil)
        #expect(parse(#"{"type":"input_audio_buffer.speech_started"}"#) == nil)
        #expect(parse("not json at all") == nil)
        #expect(parse(#"{"no_type":true}"#) == nil)
        #expect(parse(#"{"type":"conversation.item.added","item":{"type":"message"}}"#) == nil)
        #expect(parse(#"{"type":"conversation.item.added","item":{"id":"x","type":"message","role":"system"}}"#) == nil)
    }
}
