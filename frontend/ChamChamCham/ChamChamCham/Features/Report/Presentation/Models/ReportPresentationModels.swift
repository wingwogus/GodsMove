//
//  ReportPresentationModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ReportMetricPresentation: Identifiable, Hashable, Sendable {
    let title: String
    let value: String

    var id: String { title }
}

struct ReportChartSection: Identifiable, Hashable, Sendable {
    let title: String
    let model: ReportChartModel

    var id: String { title }
}

struct ReportDetailPresentation: Hashable, Sendable {
    let metrics: [ReportMetricPresentation]
    let charts: [ReportChartSection]

    init(detail: FarmingWorkReportDetail) {
        var metrics = [ReportMetricPresentation(
            title: "총 작업 횟수",
            value: "\(detail.statistics.common.recordCount)회"
        )]
        var charts: [ReportChartSection] = []

        switch detail.key.workType {
        case .planting:
            if let statistics = detail.statistics.planting {
                for method in statistics.propagationMethods {
                    if let quantity = method.totalQuantity,
                       let unit = method.quantityUnit,
                       quantity > 0 {
                        metrics.append(ReportMetricPresentation(
                            title: "\(method.label) 심은 양",
                            value: ReportValueFormatter.value(quantity, unit: unit)
                        ))
                    }
                }
                Self.appendDistributionChart(
                    title: "진행한 심기 방법",
                    values: statistics.plantingMethodDistribution,
                    to: &charts
                )
                Self.appendChart(
                    title: "진행한 모종 번식법",
                    data: statistics.propagationMethods.map {
                        ReportChartDatum(
                            code: $0.code,
                            label: $0.label,
                            value: Decimal($0.recordCount),
                            unit: "회"
                        )
                    },
                    to: &charts
                )
            }

        case .watering:
            if let statistics = detail.statistics.watering {
                if let mostFrequent = statistics.amountDistribution.sorted(by: Self.distributionOrder).first,
                   mostFrequent.count > 0 {
                    metrics.append(ReportMetricPresentation(
                        title: "가장 자주 준 물의 양",
                        value: mostFrequent.label
                    ))
                }
                Self.appendDistributionChart(title: "진행한 물주기 방식", values: statistics.methodDistribution, to: &charts)
                Self.appendDistributionChart(title: "물 준 양", values: statistics.amountDistribution, to: &charts)
            }

        case .fertilizing:
            if let statistics = detail.statistics.fertilizing {
                if let total = statistics.totalAmountKg, total > 0 {
                    metrics.append(ReportMetricPresentation(
                        title: "총 비료 사용량",
                        value: ReportValueFormatter.value(total, unit: "kg")
                    ))
                }
                Self.appendDistributionChart(title: "진행한 비료주기 방식", values: statistics.methodDistribution, to: &charts)
                Self.appendChart(
                    title: "각 비료 사용 횟수",
                    data: statistics.materialCategories.map {
                        ReportChartDatum(
                            code: $0.code,
                            label: $0.label,
                            value: Decimal($0.recordCount),
                            unit: "회"
                        )
                    },
                    to: &charts
                )
                Self.appendChart(
                    title: "각 비료 사용량",
                    data: statistics.materialCategories.map {
                        ReportChartDatum(code: $0.code, label: $0.label, value: $0.amountKg, unit: "kg")
                    },
                    to: &charts
                )
            }

        case .pestControl:
            if let statistics = detail.statistics.pestControl {
                for amount in statistics.pesticideAmounts where amount.amount > 0 {
                    metrics.append(ReportMetricPresentation(
                        title: "농약 사용량 (\(amount.unit))",
                        value: ReportValueFormatter.value(amount.amount, unit: amount.unit)
                    ))
                }
                if let total = statistics.totalSprayAmountMl, total > 0 {
                    metrics.append(ReportMetricPresentation(
                        title: "총 살포량",
                        value: ReportValueFormatter.value(total, unit: "mL")
                    ))
                }
                Self.appendDistributionChart(title: "사용한 약제 종류", values: statistics.categoryDistribution, to: &charts)
                let amountsByUnit = Dictionary(grouping: statistics.categoryAmounts, by: \.unit)
                let amountUnits = amountsByUnit.keys.sorted()
                for unit in amountUnits {
                    let title = amountUnits.count > 1 ? "각 약제 사용량 (\(unit))" : "각 약제 사용량"
                    Self.appendChart(
                        title: title,
                        data: (amountsByUnit[unit] ?? []).map {
                            ReportChartDatum(
                                code: $0.categoryCode,
                                label: $0.categoryLabel,
                                value: $0.amount,
                                unit: unit
                            )
                        },
                        to: &charts
                    )
                }
                Self.appendChart(
                    title: "대상 병해충",
                    data: statistics.targets.map {
                        ReportChartDatum(
                            code: $0.target,
                            label: $0.target,
                            value: Decimal($0.count),
                            unit: "회"
                        )
                    },
                    to: &charts
                )
            }

        case .weeding:
            if let statistics = detail.statistics.weeding {
                Self.appendDistributionChart(title: "진행한 잡초 관리 방식", values: statistics.methodDistribution, to: &charts)
            }

        case .harvest:
            if let statistics = detail.statistics.harvest {
                if let total = statistics.totalAmountKg, total > 0 {
                    metrics.append(ReportMetricPresentation(
                        title: "총 수확량",
                        value: ReportValueFormatter.value(total, unit: "kg")
                    ))
                }
                if let months = statistics.finalGrowthPeriodMonths, months > 0 {
                    metrics.append(ReportMetricPresentation(title: "재배 기간", value: "\(months)개월"))
                }
                Self.appendChart(
                    title: "수확 부위 종류",
                    data: statistics.medicinalParts.map {
                        ReportChartDatum(
                            code: $0.code,
                            label: $0.label,
                            value: Decimal($0.recordCount),
                            unit: "회"
                        )
                    },
                    to: &charts
                )
                Self.appendDistributionChart(
                    title: "재배 개월에 따른 수확량",
                    values: statistics.growthPeriodDistribution,
                    to: &charts
                )
            }

        case .pruning, .etc:
            break
        }

        self.metrics = metrics
        self.charts = charts
    }

    private static func appendDistributionChart(
        title: String,
        values: [ReportCountDistribution],
        to charts: inout [ReportChartSection]
    ) {
        appendChart(
            title: title,
            data: values.map {
                ReportChartDatum(
                    code: $0.code,
                    label: $0.label,
                    value: Decimal($0.count),
                    unit: "회"
                )
            },
            to: &charts
        )
    }

    private static func appendChart(
        title: String,
        data: [ReportChartDatum],
        to charts: inout [ReportChartSection]
    ) {
        guard let model = ReportChartModel(title: title, data: data) else { return }
        charts.append(ReportChartSection(title: title, model: model))
    }

    private static func distributionOrder(
        _ lhs: ReportCountDistribution,
        _ rhs: ReportCountDistribution
    ) -> Bool {
        if lhs.count != rhs.count { return lhs.count > rhs.count }
        if lhs.code != rhs.code { return lhs.code < rhs.code }
        return lhs.label < rhs.label
    }
}

struct ReportCoachingSectionPresentation: Identifiable, Equatable, Sendable {
    let title: String
    let messages: [String]

    var id: String { title }
}

struct ReportCoachingPresentation: Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case active
        case pending
        case ready
        case failed
        case stale
        case unavailable
    }

    let kind: Kind
    let title: String
    let message: String
    let sections: [ReportCoachingSectionPresentation]
    let showsProgress: Bool
    let showsRefresh: Bool
    let showsRegenerate: Bool
    let regenerateEnabled: Bool

    init(
        cycleStatus: ReportCycleStatus,
        feedback: ReportFeedbackSnapshot?,
        isOffline: Bool
    ) {
        title = "참참참의 코칭"

        if cycleStatus == .active {
            kind = .active
            message = "재배 주기가 끝나면 기록을 바탕으로 맞춤 코칭을 알려드릴게요."
            sections = []
            showsProgress = false
            showsRefresh = false
            showsRegenerate = false
            regenerateEnabled = false
            return
        }

        guard cycleStatus == .completed else {
            kind = .unavailable
            message = "리포트 상태를 확인할 수 없어요. 잠시 후 다시 시도해주세요."
            sections = []
            showsProgress = false
            showsRefresh = false
            showsRegenerate = false
            regenerateEnabled = false
            return
        }

        switch feedback?.state ?? .pending {
        case .pending:
            kind = .pending
            message = isOffline
                ? "오프라인 상태예요. 온라인에서 코칭 준비 상태를 다시 확인할 수 있어요."
                : "기록을 살펴보고 맞춤 코칭을 준비하고 있어요."
            sections = []
            showsProgress = !isOffline
            showsRefresh = !isOffline
            showsRegenerate = false
            regenerateEnabled = false

        case .ready:
            kind = .ready
            message = feedback?.content?.summary ?? ""
            sections = Self.sections(content: feedback?.content)
            showsProgress = false
            showsRefresh = false
            showsRegenerate = false
            regenerateEnabled = false

        case .failed:
            kind = .failed
            message = isOffline
                ? "온라인 상태에서 코칭을 다시 만들 수 있어요."
                : "코칭을 만드는 중 문제가 생겼어요. 다시 만들어볼까요?"
            sections = []
            showsProgress = false
            showsRefresh = false
            showsRegenerate = true
            regenerateEnabled = !isOffline

        case .stale:
            kind = .stale
            message = isOffline
                ? "온라인 상태에서 최신 기록으로 코칭을 다시 만들 수 있어요."
                : "기록이 바뀌었어요. 최신 기록으로 코칭을 다시 만들어주세요."
            sections = []
            showsProgress = false
            showsRefresh = false
            showsRegenerate = true
            regenerateEnabled = !isOffline

        case .unsupported:
            kind = .unavailable
            message = "코칭 상태를 확인할 수 없어요. 잠시 후 다시 시도해주세요."
            sections = []
            showsProgress = false
            showsRefresh = !isOffline
            showsRegenerate = false
            regenerateEnabled = false
        }
    }

    private static func sections(
        content: ReportFeedbackContent?
    ) -> [ReportCoachingSectionPresentation] {
        guard let content else { return [] }
        let candidates: [(String, [String])] = [
            ("잘한 점", content.strengths),
            ("이전과 비교", content.comparisons),
            ("개선하면 좋은 점", content.improvements),
            ("다음에 해볼 일", content.nextActions),
        ]
        return candidates.compactMap { title, messages in
            let visible = messages.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            guard !visible.isEmpty else { return nil }
            return ReportCoachingSectionPresentation(title: title, messages: visible)
        }
    }
}

enum ReportRecordHistoryPresentation {
    static let title = "기록 내역"
    static let message = "이 리포트에 포함된 기록을 불러오는 기능을 준비하고 있어요."
}

enum ReportDetailLayout {
    static let compactWidth: CGFloat = 340
    static let compactHorizontalInset: CGFloat = 16
    static let regularHorizontalInset: CGFloat = 20
    static let metricMinimumWidth: CGFloat = 132
    static let usesFixedContentHeight = false

    static func horizontalInset(availableWidth: CGFloat) -> CGFloat {
        availableWidth <= compactWidth ? compactHorizontalInset : regularHorizontalInset
    }
}
