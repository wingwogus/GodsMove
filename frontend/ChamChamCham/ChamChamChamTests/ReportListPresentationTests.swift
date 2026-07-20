//
//  ReportListPresentationTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Report list presentation")
struct ReportListPresentationTests {
    @Test("filter chips show defaults or the selected single value")
    func filterChips() {
        let farms = ReportFixtures.farms().map(ReportFarmFilterOption.init(farm:))
        let defaults = ReportFilterChipPresentation.all(filter: ReportFilter(), farms: farms)
        let selected = ReportFilterChipPresentation.all(
            filter: ReportFilter(
                farmIds: [farms[0].id],
                cropIds: [farms[0].crops[0].id],
                workTypes: [.pestControl]
            ),
            farms: farms
        )

        #expect(defaults.map(\.title) == ["작물", "영농 활동", "농장"])
        #expect(defaults.allSatisfy { !$0.isSelected })
        #expect(selected.map(\.title) == ["황기", "병해충 관리", "북쪽 밭"])
        #expect(selected.allSatisfy { $0.isSelected })
    }

    @Test("multi-selected work types collapse into a '라벨 외 N' chip title")
    func multiSelectedWorkTypeChip() {
        let farms = ReportFixtures.farms().map(ReportFarmFilterOption.init(farm:))
        let chips = ReportFilterChipPresentation.all(
            filter: ReportFilter(workTypes: [.watering, .pestControl, .harvest]),
            farms: farms
        )

        let workTypeChip = chips.first { $0.kind == .workType }
        #expect(workTypeChip?.title == "물주기 외 2")
        #expect(workTypeChip?.isSelected == true)
    }

    @Test("multi-selected farms and crops collapse into a '이름 외 N' chip title")
    func multiSelectedFarmAndCropChip() {
        let farms = ReportFixtures.farms().map(ReportFarmFilterOption.init(farm:))
        let chips = ReportFilterChipPresentation.all(
            filter: ReportFilter(
                farmIds: Set(farms.map(\.id)),
                cropIds: Set(farms.flatMap(\.crops).map(\.id))
            ),
            farms: farms
        )

        let farmChip = chips.first { $0.kind == .farm }
        let cropChip = chips.first { $0.kind == .crop }
        #expect(farmChip?.title == "북쪽 밭 외 1")
        #expect(cropChip?.title == "황기 외 1")
    }

    @Test("card copy uses only summary fields and detects its thumbnail fallback")
    func cardContent() {
        let content = ReportListCardPresentation(summary: ReportFixtures.summary(workType: .watering))

        #expect(content.title == "물주기")
        #expect(content.badges == ["황기", "북쪽 밭"])
        #expect(content.periodParts == ["2026.04.01", "2026.10.31"])
        #expect(content.thumbnailURL == nil)
    }

    @Test("an active period ends with progress copy while completed periods use the end date")
    func periodFormatting() {
        let completed = ReportListCardPresentation(summary: ReportFixtures.summary())
        let activeSummary = FarmingWorkReportSummary(
            key: WorkReportKey(reportId: UUID(), workType: .harvest),
            status: .active,
            farmId: ReportFixtures.farmId,
            farmName: "북쪽 밭",
            cropId: ReportFixtures.cropId,
            cropName: "황기",
            startsAt: completedDate("2026-06-01"),
            endsAt: nil,
            workTypeLabel: "수확",
            recordCount: 1,
            lastWorkedOn: nil,
            thumbnailUrl: "https://example.com/report.jpg"
        )
        let active = ReportListCardPresentation(summary: activeSummary)

        #expect(completed.periodParts.last == "2026.10.31")
        #expect(active.periodParts == ["2026.06.01", "진행 중"])
        #expect(active.thumbnailURL?.absoluteString == "https://example.com/report.jpg")
    }

    @Test("list insets adapt on iPhone SE without introducing a fixed content height")
    func adaptiveLayout() {
        #expect(ReportListLayout.horizontalInset(availableWidth: 320) == 16)
        #expect(ReportListLayout.horizontalInset(availableWidth: 393) == 20)
        #expect(ReportListLayout.cardWidth(availableWidth: 320) == 288)
        #expect(!ReportListLayout.usesFixedContentHeight)
    }

    private func completedDate(_ value: String) -> Date {
        ReportDateParser.localDate(from: value)!
    }
}
