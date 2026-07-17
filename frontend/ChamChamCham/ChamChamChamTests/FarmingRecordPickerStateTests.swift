//
//  FarmingRecordPickerStateTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import CoreGraphics
import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("Farming record picker state")
struct FarmingRecordPickerStateTests {
    private struct Unused: Error {}

    /// Crop filtering is server-side (mirrors `RecordListViewModel`), so the stub filters `records` by
    /// `query.cropIds` the same way the real endpoint would.
    private struct StubRecordRepository: RecordRepository {
        let records: [FarmingRecordSummary]
        let activeCrops: [ActiveCrop]

        func fetchRecords(_ query: RecordQuery) async throws -> RecordPage {
            let items = query.cropIds.isEmpty
                ? records
                : records.filter { query.cropIds.contains($0.cropId) }
            return RecordPage(items: items, nextCursor: nil)
        }

        func fetchActiveCrops() async throws -> [ActiveCrop] { activeCrops }

        func fetchDetail(id: UUID) async throws -> RecordDetail { throw Unused() }
        func fetchCoaching(id: UUID) async throws -> RecordCoaching { throw Unused() }
        func deleteRecord(id: UUID) async throws { throw Unused() }
        func fetchFarmCrops() async throws -> [FarmWithCrops] { throw Unused() }
        func searchPesticides(keyword: String?) async throws -> [Pesticide] { throw Unused() }
        func fetchPests(pesticideId: UUID) async throws -> [Pest] { throw Unused() }
        func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID { throw Unused() }
    }

    private static let strawberryId = UUID(uuidString: "AAAAAAAA-0000-0000-0000-000000000001")!
    private static let tomatoId = UUID(uuidString: "BBBBBBBB-0000-0000-0000-000000000002")!

    private static func makeRecords() -> [FarmingRecordSummary] {
        [
            FarmingRecordSummary(
                id: UUID(uuidString: "0FC55A80-93F2-4C74-939B-B424842AD35C")!,
                cropId: strawberryId,
                cropName: "딸기",
                workType: .watering,
                memoPreview: "관수량과 생육 상태 기록",
                thumbnailUrl: nil,
                weatherCondition: "맑음",
                weatherTemperature: 22,
                workedAt: Date(timeIntervalSince1970: 1_720_000_000)
            ),
            FarmingRecordSummary(
                id: UUID(uuidString: "5C2877A9-0EA3-4851-860B-E33942D6D97E")!,
                cropId: tomatoId,
                cropName: "토마토",
                workType: .pestControl,
                memoPreview: "병해충 확인 및 방제 메모",
                thumbnailUrl: nil,
                weatherCondition: "흐림",
                weatherTemperature: 20,
                workedAt: Date(timeIntervalSince1970: 1_719_900_000)
            ),
            FarmingRecordSummary(
                id: UUID(uuidString: "2F41D692-A887-422E-9DDF-0EEAD2C63C8D")!,
                cropId: tomatoId,
                cropName: "토마토",
                workType: .harvest,
                memoPreview: "작성 내용은 최대 2줄입니다.",
                thumbnailUrl: nil,
                weatherCondition: "맑음",
                weatherTemperature: 24,
                workedAt: Date(timeIntervalSince1970: 1_719_800_000)
            ),
        ]
    }

    private static func makeActiveCrops(from records: [FarmingRecordSummary]) -> [ActiveCrop] {
        var seen = Set<UUID>()
        return records.compactMap { record in
            guard seen.insert(record.cropId).inserted else { return nil }
            return ActiveCrop(id: record.cropId, name: record.cropName)
        }
    }

    @Test("crop filter re-queries the server and clears an invisible selection")
    func cropFilterClearsInvisibleSelection() async {
        let records = Self.makeRecords()
        let repository = StubRecordRepository(records: records, activeCrops: Self.makeActiveCrops(from: records))
        let state = FarmingRecordPickerState(repository: repository)
        await state.load()

        state.selectRecord(records[0].id)
        #expect(state.selectedRecordID == records[0].id)

        await state.selectCrop(Self.tomatoId)
        #expect(state.selectedRecordID == nil)
        #expect(state.filteredRecords.allSatisfy { $0.cropId == Self.tomatoId })

        state.selectRecord(records[1].id)
        state.searchText = "검색 결과 없음"
        #expect(state.selectedRecordID == nil)
        #expect(state.filteredRecords.isEmpty)
    }

    @Test("picker geometry matches the selected-state capture")
    func geometry() {
        #expect(FarmingRecordPickerView.Layout.horizontalInset == 20)
        #expect(FarmingRecordPickerView.Layout.filterTopInset == 16)
        #expect(FarmingRecordPickerView.Layout.chipAreaHeight == 64)
        #expect(FarmingRecordPickerView.Layout.cardSpacing == 16)
        #expect(FarmingRecordPickerView.Layout.listTopInset == 8)
    }

    @Test("an initial selection preloads its crop filter and keeps the matching card list")
    func selectedCropSample() async {
        let records = Self.makeRecords()
        let selected = records[1]
        let repository = StubRecordRepository(records: records, activeCrops: Self.makeActiveCrops(from: records))
        let state = FarmingRecordPickerState(repository: repository, selectedRecord: selected)
        await state.load()

        #expect(state.filteredRecords.count == 2)
        #expect(state.filteredRecords.allSatisfy { $0.cropId == selected.cropId })
    }
}
