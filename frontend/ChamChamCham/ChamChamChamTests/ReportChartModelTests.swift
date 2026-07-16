//
//  ReportChartModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Report chart normalization")
struct ReportChartModelTests {
    @Test("non-positive values are removed and ties use code then label")
    func filteringAndStableSort() throws {
        let model = try #require(ReportChartModel(
            title: "분포",
            data: [
                .init(code: "B", label: "나", value: 2, unit: "회"),
                .init(code: "A", label: "가", value: 2, unit: "회"),
                .init(code: "ZERO", label: "영", value: 0, unit: "회"),
                .init(code: "NEGATIVE", label: "음수", value: -1, unit: "회"),
                .init(code: "C", label: "다", value: 3, unit: "회"),
            ]
        ))

        #expect(model.entries.map(\.code) == ["C", "A", "B"])
        #expect(model.style == .stackedBar)
        #expect(model.primary?.formattedValue == "3회")
    }

    @Test("four through six values use a semi donut")
    func semiDonutRange() throws {
        for count in 4...6 {
            let model = try #require(ReportChartModel(
                title: "분포",
                data: (1...count).map {
                    .init(code: "\($0)", label: "항목 \($0)", value: Decimal($0), unit: "회")
                }
            ))
            #expect(model.style == .semiDonut)
            #expect(model.entries.count == count)
        }
    }

    @Test("more than six values become top five plus 기타 using the actual total")
    func topFiveAndOther() throws {
        let model = try #require(ReportChartModel(
            title: "분포",
            data: (1...8).map {
                .init(code: "C\($0)", label: "항목 \($0)", value: Decimal($0), unit: "kg")
            }
        ))

        #expect(model.style == .semiDonut)
        #expect(model.entries.map(\.value) == [8, 7, 6, 5, 4, 6])
        #expect(model.entries.last?.label == "기타")
        #expect(model.entries.last?.formattedValue == "6kg")
        #expect(abs(model.entries.map(\.fraction).reduce(0, +) - 1) < 0.000_001)
        #expect(abs((model.entries.first?.fraction ?? 0) - (8.0 / 36.0)) < 0.000_001)
    }

    @Test("empty input and mixed units do not create a chart")
    func invalidInputs() {
        #expect(ReportChartModel(title: "빈 값", data: []) == nil)
        #expect(ReportChartModel(title: "0", data: [
            .init(code: "A", label: "가", value: 0, unit: "회")
        ]) == nil)
        #expect(ReportChartModel(title: "단위 혼합", data: [
            .init(code: "G", label: "그램", value: 10, unit: "g"),
            .init(code: "ML", label: "밀리리터", value: 20, unit: "ml"),
        ]) == nil)
    }

    @Test("collapsed chart highlights its primary entry and expanded chart shows the full legend")
    func expansionPresentation() throws {
        let model = try #require(ReportChartModel(
            title: "분포",
            data: [
                .init(code: "A", label: "가", value: 3, unit: "회"),
                .init(code: "B", label: "나", value: 2, unit: "회"),
                .init(code: "C", label: "다", value: 1, unit: "회"),
            ]
        ))

        #expect(model.highlightedEntry(isExpanded: false) == model.primary)
        #expect(model.legendEntries(isExpanded: false).isEmpty)
        #expect(model.highlightedEntry(isExpanded: true) == nil)
        #expect(model.legendEntries(isExpanded: true) == model.entries)
    }
}
