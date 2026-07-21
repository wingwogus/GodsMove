//
//  RecordComposeVoiceReviewTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

/// 텍스트 작성 폼(RecordComposeViewModel)을 음성 검토 화면으로 재사용할 때의 계약:
/// 프리필 적용, missingFields 즉시 표시, confirm 라우팅(entryMode VOICE + 날씨 병합),
/// VOICE_002 처리.
@MainActor
@Suite("RecordComposeViewModel 음성 검토 재사용")
struct RecordComposeVoiceReviewTests {

    private struct Unused: Error {}

    // MARK: - 스텁

    private struct StubRecordRepository: RecordRepository {
        var farms: [FarmWithCrops] = []
        var pests: [Pest] = []

        func fetchRecords(_ query: RecordQuery) async throws -> RecordPage { throw Unused() }
        func fetchDetail(id: UUID) async throws -> RecordDetail { throw Unused() }
        func fetchCoaching(id: UUID) async throws -> RecordCoaching { throw Unused() }
        func deleteRecord(id: UUID) async throws { throw Unused() }
        func fetchActiveCrops() async throws -> [ActiveCrop] { throw Unused() }
        func fetchFarmCrops() async throws -> [FarmWithCrops] { farms }
        func searchPesticides(keyword: String?) async throws -> [Pesticide] { [] }
        func fetchPests(pesticideId: UUID) async throws -> [Pest] { pests }
        func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID { throw Unused() }
        func fetchEditPrefill(id: UUID) async throws -> VoiceRecordPrefill { throw Unused() }
        func updateRecord(id: UUID, _ request: SaveRecordRequestDTO) async throws -> UUID { throw Unused() }
    }

    private struct StubWeatherRepository: WeatherRepository {
        var weather: CurrentWeather

        func fetchHome(farmId: UUID?) async throws -> CurrentWeather { weather }
        func fetchDetail(farmId: UUID?) async throws -> WeatherDetail { throw Unused() }
    }

    private struct StubMediaUpload: MediaUploadRepository {
        func uploadProfileImage(
            _ imageData: Data, originalFilename: String?
        ) async throws -> UploadedImageResponseDTO { throw Unused() }
        func uploadCommunityImage(
            _ imageData: Data, originalFilename: String?
        ) async throws -> UploadedImageResponseDTO { throw Unused() }
        func uploadFarmingRecordImage(
            _ imageData: Data, originalFilename: String?
        ) async throws -> UploadedImageResponseDTO { throw Unused() }
    }

    /// 저장 요청을 기록하는 spy saver.
    private actor SpySaver: RecordSaver {
        nonisolated let entryMode: EntryMode
        private let result: Result<UUID, any Error>
        private(set) var saved: [SaveRecordRequestDTO] = []

        init(entryMode: EntryMode, result: Result<UUID, any Error>) {
            self.entryMode = entryMode
            self.result = result
        }

        func save(_ request: SaveRecordRequestDTO) async throws -> UUID {
            saved.append(request)
            return try result.get()
        }
    }

    // MARK: - 픽스처

    private static let farmId = UUID()
    private static let cropId = UUID()

    private static var farm: FarmWithCrops {
        FarmWithCrops(
            farmId: farmId, farmName: "뒷밭",
            crops: [ActiveCrop(id: cropId, name: "상추"), ActiveCrop(id: UUID(), name: "감자")]
        )
    }

    private static var prefill: VoiceRecordPrefill {
        VoiceRecordPrefill(
            farmId: farmId,
            cropId: cropId,
            workType: .watering,
            workedAt: Date(timeIntervalSince1970: 1_784_100_000),
            memo: "상추에 물을 흠뻑 줬다",
            irrigationAmount: .normal,
            irrigationMethod: .drip,
            missingFields: []
        )
    }

    private func makeViewModel(
        prefill: VoiceRecordPrefill?,
        saver: SpySaver,
        farms: [FarmWithCrops] = [farm]
    ) -> RecordComposeViewModel {
        RecordComposeViewModel(
            repository: StubRecordRepository(farms: farms),
            weatherRepository: StubWeatherRepository(
                weather: CurrentWeather(
                    temperature: 27,
                    condition: WeatherCondition(code: "CLEAR", text: "맑음"),
                    minTemperature: nil,
                    maxTemperature: nil
                )
            ),
            mediaUpload: StubMediaUpload(),
            saver: saver,
            prefill: prefill
        )
    }

    @discardableResult
    private func waitUntil(_ condition: () -> Bool) async -> Bool {
        for _ in 0..<500 {
            if condition() { return true }
            try? await Task.sleep(for: .milliseconds(2))
        }
        return condition()
    }

    // MARK: - 테스트

    @Test("프리필: onAppear 후 농지/작물/작업/메모/상세가 채워지고 날씨 조회가 돈다")
    func prefillApplied() async {
        let saver = SpySaver(entryMode: .voice, result: .success(UUID()))
        let vm = makeViewModel(prefill: Self.prefill, saver: saver)

        await vm.onAppear()

        #expect(vm.selectedFarmId == Self.farmId)
        #expect(vm.selectedCropId == Self.cropId)
        #expect(vm.workType == .watering)
        #expect(vm.memo == "상추에 물을 흠뻑 줬다")
        #expect(vm.irrigationAmount == .normal)
        #expect(vm.irrigationMethod == .drip)
        #expect(vm.showValidation == false) // missingFields 없음 → 검증 문구 미노출
        #expect(await waitUntil { vm.weather != nil }) // farmId didSet → 날씨 자동 조회
    }

    @Test("프리필: 실소유가 아닌 farm/crop id는 버리고, missingFields가 있으면 검증을 켠다")
    func prefillDropsUnknownIdsAndShowsMissing() async {
        var prefill = Self.prefill
        prefill.farmId = UUID() // 목록에 없는 농지
        prefill.cropId = UUID()
        prefill.missingFields = ["farmId", "cropId"]
        let saver = SpySaver(entryMode: .voice, result: .success(UUID()))
        let vm = makeViewModel(prefill: prefill, saver: saver)

        await vm.onAppear()

        // 프리필이 버려지면 단일 농지 자동 선택 규칙이 이어받는다 (농지 1개 픽스처).
        #expect(vm.selectedFarmId == Self.farmId)
        #expect(vm.selectedCropId == nil)
        #expect(vm.showValidation)
    }

    @Test("제출은 saver로 라우팅된다: entryMode VOICE + 날씨 병합 (Gotcha 1)")
    func submitRoutesThroughVoiceSaver() async {
        let saver = SpySaver(entryMode: .voice, result: .success(UUID()))
        let vm = makeViewModel(prefill: Self.prefill, saver: saver)

        await vm.onAppear()
        await waitUntil { vm.weather != nil }
        vm.memo = String(repeating: "물주기 작업 내용 기록. ", count: 3) // 30자 이상

        await vm.submit()

        let saved = await saver.saved
        #expect(saved.count == 1)
        #expect(saved.first?.entryMode == "VOICE")
        #expect(saved.first?.weatherTemperature == 27)
        #expect(saved.first?.weatherCondition == "맑음")
        #expect(saved.first?.watering?.irrigationMethod == "DRIP")
        #expect(vm.createdRecordId != nil)
    }

    @Test("confirm이 VOICE_002면 재시도 없이 voiceSessionInvalidated만 켠다")
    func alreadyProcessedConfirm() async {
        let saver = SpySaver(entryMode: .voice, result: .failure(VoiceSessionError.alreadyProcessed))
        let vm = makeViewModel(prefill: Self.prefill, saver: saver)

        await vm.onAppear()
        await waitUntil { vm.weather != nil }
        vm.memo = String(repeating: "물주기 작업 내용 기록. ", count: 3)

        await vm.submit()

        #expect(vm.voiceSessionInvalidated)
        #expect(vm.createdRecordId == nil)
        #expect(vm.errorMessage == nil) // 에러 배너가 아니라 플로우 종료 신호
        #expect(await saver.saved.count == 1)
    }

    @Test("saver를 안 주면 기존 텍스트 작성과 동일하게 MANUAL로 저장된다")
    func defaultSaverKeepsManualBehavior() async {
        // 기본 saver는 repository.createRecord를 부른다 — 여기서는 라우팅만 확인.
        let saver = SpySaver(entryMode: .manual, result: .success(UUID()))
        let vm = makeViewModel(prefill: nil, saver: saver)

        await vm.onAppear()
        vm.selectedFarmId = Self.farmId
        vm.selectedCropId = Self.cropId
        vm.workType = .watering
        vm.memo = String(repeating: "물주기 작업 내용 기록. ", count: 3)
        await waitUntil { vm.weather != nil }

        await vm.submit()

        #expect(await saver.saved.first?.entryMode == "MANUAL")
    }
}
