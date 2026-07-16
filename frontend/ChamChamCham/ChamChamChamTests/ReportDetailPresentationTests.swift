//
//  ReportDetailPresentationTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Report detail presentation")
struct ReportDetailPresentationTests {
    @Test("active cycles explain when coaching becomes available")
    func activeCopy() {
        let presentation = ReportCoachingPresentation(
            cycleStatus: .active,
            feedback: nil,
            isOffline: false
        )

        #expect(presentation.kind == .active)
        #expect(presentation.title == "참참참의 코칭")
        #expect(presentation.message.contains("재배 주기가 끝나면"))
        #expect(!presentation.showsProgress)
        #expect(!presentation.showsRefresh)
    }

    @Test("pending, failed, stale, and offline states expose safe actions")
    func actionStates() {
        let pending = ReportCoachingPresentation(
            cycleStatus: .completed,
            feedback: .init(state: .pending, content: nil),
            isOffline: false
        )
        let failed = ReportCoachingPresentation(
            cycleStatus: .completed,
            feedback: .init(state: .failed, content: nil),
            isOffline: false
        )
        let staleOffline = ReportCoachingPresentation(
            cycleStatus: .completed,
            feedback: .init(state: .stale, content: nil),
            isOffline: true
        )

        #expect(pending.kind == .pending)
        #expect(pending.showsProgress)
        #expect(pending.showsRefresh)
        #expect(!pending.showsRegenerate)
        #expect(failed.showsRegenerate)
        #expect(failed.regenerateEnabled)
        #expect(staleOffline.showsRegenerate)
        #expect(!staleOffline.regenerateEnabled)
        #expect(staleOffline.message.contains("온라인"))
    }

    @Test("ready coaching keeps role messages together in the approved order")
    func readySectionOrder() {
        let content = ReportFeedbackContent(
            summary: "전체 흐름이 안정적이에요.",
            comparisons: ["지난 주기보다 간격이 일정해요."],
            strengths: ["시기를 잘 맞췄어요.", "기록이 꾸준해요."],
            improvements: ["사진을 조금 더 남겨보세요."],
            nextActions: ["다음 작업 날짜를 정해보세요."]
        )
        let presentation = ReportCoachingPresentation(
            cycleStatus: .completed,
            feedback: .init(state: .ready, content: content),
            isOffline: false
        )

        #expect(presentation.kind == .ready)
        #expect(presentation.message == "전체 흐름이 안정적이에요.")
        #expect(presentation.sections.map(\.title) == [
            "잘한 점", "이전과 비교", "개선하면 좋은 점", "다음에 해볼 일",
        ])
        #expect(presentation.sections[0].messages == ["시기를 잘 맞췄어요.", "기록이 꾸준해요."])
        #expect(!presentation.showsProgress)
        #expect(!presentation.showsRegenerate)
    }

    @Test("empty coaching roles are omitted without changing the remaining order")
    func emptyRolesAreOmitted() {
        let content = ReportFeedbackContent(
            summary: "요약",
            comparisons: [],
            strengths: ["장점"],
            improvements: [],
            nextActions: ["다음 행동"]
        )
        let presentation = ReportCoachingPresentation(
            cycleStatus: .completed,
            feedback: .init(state: .ready, content: content),
            isOffline: false
        )

        #expect(presentation.message == "요약")
        #expect(presentation.sections.map(\.title) == ["잘한 점", "다음에 해볼 일"])
    }

    @Test("record history communicates the unavailable production endpoint")
    func historyUnavailableCopy() {
        #expect(ReportRecordHistoryPresentation.title == "기록 내역")
        #expect(ReportRecordHistoryPresentation.message == "이 리포트에 포함된 기록을 불러오는 기능을 준비하고 있어요.")
    }

    @Test("compact detail layouts stay adaptive without fixed content height")
    func adaptiveLayout() {
        #expect(ReportDetailLayout.horizontalInset(availableWidth: 320) == 16)
        #expect(ReportDetailLayout.horizontalInset(availableWidth: 390) == 20)
        #expect(ReportDetailLayout.metricMinimumWidth <= 140)
        #expect(!ReportDetailLayout.usesFixedContentHeight)
    }
}
