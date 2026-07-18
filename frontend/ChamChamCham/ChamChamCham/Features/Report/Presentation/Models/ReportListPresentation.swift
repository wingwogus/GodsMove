//
//  ReportListPresentation.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

enum ReportFilterKind: Int, Identifiable {
    case crop
    case workType
    case farm

    var id: Int { rawValue }
}

struct ReportFilterChipPresentation: Equatable {
    let kind: ReportFilterKind
    let title: String
    let isSelected: Bool

    static func all(
        filter: ReportFilter,
        farms: [ReportFarmFilterOption]
    ) -> [ReportFilterChipPresentation] {
        let crops = farms.flatMap(\.crops)
        return [
            ReportFilterChipPresentation(
                kind: .crop,
                title: namesChipTitle(
                    names: crops.filter { filter.cropIds.contains($0.id) }.map(\.name),
                    placeholder: "작물"
                ),
                isSelected: !filter.cropIds.isEmpty
            ),
            ReportFilterChipPresentation(
                kind: .workType,
                title: workTypeChipTitle(for: filter.workTypes),
                isSelected: !filter.workTypes.isEmpty
            ),
            ReportFilterChipPresentation(
                kind: .farm,
                title: namesChipTitle(
                    names: farms.filter { filter.farmIds.contains($0.id) }.map(\.name),
                    placeholder: "농장"
                ),
                isSelected: !filter.farmIds.isEmpty
            ),
        ]
    }

    private static func namesChipTitle(names: [String], placeholder: String) -> String {
        guard !names.isEmpty else { return placeholder }
        return names.count == 1 ? names[0] : "\(names[0]) 외 \(names.count - 1)"
    }

    private static func workTypeChipTitle(for workTypes: Set<WorkType>) -> String {
        guard !workTypes.isEmpty else { return "영농 활동" }
        let labels = WorkType.allCases.filter(workTypes.contains).map(\.label)
        return labels.count == 1 ? labels[0] : "\(labels[0]) 외 \(labels.count - 1)"
    }
}

struct ReportListCardPresentation: Equatable {
    let title: String
    let badges: [String]
    let periodParts: [String]
    let thumbnailURL: URL?
    let workType: WorkType

    init(summary: FarmingWorkReportSummary) {
        title = summary.workTypeLabel
        workType = summary.key.workType
        badges = [summary.cropName, summary.farmName]
        periodParts = [
            ReportDateParser.displayDate(summary.startsAt),
            summary.endsAt.map(ReportDateParser.displayDate) ?? "진행 중",
        ]
        if let value = summary.thumbnailUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
           !value.isEmpty,
           let url = URL(string: value),
           url.scheme == "http" || url.scheme == "https" {
            thumbnailURL = url
        } else {
            thumbnailURL = nil
        }
    }
}

enum ReportListLayout {
    static let compactWidth: CGFloat = 340
    static let regularHorizontalInset: CGFloat = 20
    static let compactHorizontalInset: CGFloat = 16
    static let cardSpacing: CGFloat = 20
    static let usesFixedContentHeight = false

    static func horizontalInset(availableWidth: CGFloat) -> CGFloat {
        availableWidth <= compactWidth ? compactHorizontalInset : regularHorizontalInset
    }

    static func cardWidth(availableWidth: CGFloat) -> CGFloat {
        max(0, availableWidth - horizontalInset(availableWidth: availableWidth) * 2)
    }
}
