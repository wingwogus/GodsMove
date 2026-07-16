//
//  ReportChartModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ReportChartDatum: Hashable, Sendable {
    let code: String
    let label: String
    let value: Decimal
    let unit: String
}

struct ReportChartEntry: Identifiable, Hashable, Sendable {
    let code: String
    let label: String
    let value: Decimal
    let unit: String
    let fraction: Double

    var id: String { "\(code)|\(label)" }
    var formattedValue: String { ReportValueFormatter.value(value, unit: unit) }
}

struct ReportChartModel: Hashable, Sendable {
    enum Style: Hashable, Sendable {
        case stackedBar
        case semiDonut
    }

    let title: String
    let style: Style
    let entries: [ReportChartEntry]

    var primary: ReportChartEntry? { entries.first }

    func highlightedEntry(isExpanded: Bool) -> ReportChartEntry? {
        isExpanded ? nil : primary
    }

    func legendEntries(isExpanded: Bool) -> [ReportChartEntry] {
        isExpanded ? entries : []
    }

    init?(title: String, data: [ReportChartDatum]) {
        let positive = data.filter { $0.value > 0 }
        guard !positive.isEmpty, Set(positive.map(\.unit)).count == 1 else { return nil }

        let sorted = positive.sorted { lhs, rhs in
            if lhs.value != rhs.value { return lhs.value > rhs.value }
            if lhs.code != rhs.code { return lhs.code < rhs.code }
            return lhs.label < rhs.label
        }
        let normalized: [ReportChartDatum]
        if sorted.count > 6 {
            let other = sorted.dropFirst(5)
            normalized = Array(sorted.prefix(5)) + [ReportChartDatum(
                code: "__OTHER__",
                label: "기타",
                value: other.reduce(Decimal.zero) { $0 + $1.value },
                unit: sorted[0].unit
            )]
        } else {
            normalized = sorted
        }

        let total = normalized.reduce(Decimal.zero) { $0 + $1.value }
        let totalDouble = NSDecimalNumber(decimal: total).doubleValue
        guard totalDouble > 0 else { return nil }

        self.title = title
        style = normalized.count <= 3 ? .stackedBar : .semiDonut
        entries = normalized.map {
            ReportChartEntry(
                code: $0.code,
                label: $0.label,
                value: $0.value,
                unit: $0.unit,
                fraction: NSDecimalNumber(decimal: $0.value).doubleValue / totalDouble
            )
        }
    }
}

enum ReportValueFormatter {
    static func value(_ value: Decimal, unit: String = "") -> String {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        let number = formatter.string(from: NSDecimalNumber(decimal: value))
            ?? NSDecimalNumber(decimal: value).stringValue
        return "\(number)\(unit)"
    }
}
