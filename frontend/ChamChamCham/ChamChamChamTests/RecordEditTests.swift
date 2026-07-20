//
//  RecordEditTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/20/26.
//

import Foundation
import Testing
@testable import ChamChamCham

/// 기록 수정(edit) 기능의 계약: 상세 응답 → 수정 프리필 매핑(raw id/enum 코드/사진 mediaId 보존),
/// `UpdateRecordSaver`가 create가 아니라 update로 라우팅되는지, 그리고 프리필이 적용된 폼이
/// 기존 사진을 mediaId 그대로 재제출하고(보존) 삭제된 사진은 페이로드에서 빠지는지를 다룬다.
@MainActor
@Suite("기록 수정")
struct RecordEditTests {

    private struct Unused: Error {}

    // MARK: - 스텁

    private struct StubRecordRepository: RecordRepository {
        var farms: [FarmWithCrops] = []

        func fetchRecords(_ query: RecordQuery) async throws -> RecordPage { throw Unused() }
        func fetchDetail(id: UUID) async throws -> RecordDetail { throw Unused() }
        func fetchCoaching(id: UUID) async throws -> RecordCoaching { throw Unused() }
        func deleteRecord(id: UUID) async throws { throw Unused() }
        func fetchActiveCrops() async throws -> [ActiveCrop] { throw Unused() }
        func fetchFarmCrops() async throws -> [FarmWithCrops] { farms }
        func searchPesticides(keyword: String?) async throws -> [Pesticide] { [] }
        func fetchPests(pesticideId: UUID) async throws -> [Pest] { [] }
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
        func uploadProfileImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO { throw Unused() }
        func uploadCommunityImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO { throw Unused() }
        func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO { throw Unused() }
    }

    /// `updateRecord`/`createRecord` 호출을 각각 세어 update 라우팅을 검증하는 spy repository.
    private actor SpyUpdateRepository: RecordRepository {
        private(set) var updateCalls: [(id: UUID, request: SaveRecordRequestDTO)] = []
        private(set) var createCallCount = 0

        nonisolated func fetchRecords(_ query: RecordQuery) async throws -> RecordPage { throw Unused() }
        nonisolated func fetchDetail(id: UUID) async throws -> RecordDetail { throw Unused() }
        nonisolated func fetchCoaching(id: UUID) async throws -> RecordCoaching { throw Unused() }
        nonisolated func deleteRecord(id: UUID) async throws { throw Unused() }
        nonisolated func fetchActiveCrops() async throws -> [ActiveCrop] { throw Unused() }
        nonisolated func fetchFarmCrops() async throws -> [FarmWithCrops] { throw Unused() }
        nonisolated func searchPesticides(keyword: String?) async throws -> [Pesticide] { [] }
        nonisolated func fetchPests(pesticideId: UUID) async throws -> [Pest] { [] }
        nonisolated func fetchEditPrefill(id: UUID) async throws -> VoiceRecordPrefill { throw Unused() }

        func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID {
            createCallCount += 1
            return UUID()
        }

        func updateRecord(id: UUID, _ request: SaveRecordRequestDTO) async throws -> UUID {
            updateCalls.append((id, request))
            return id
        }
    }

    // MARK: - 픽스처

    private static let recordId = UUID()
    private static let farmId = UUID()
    private static let cropId = UUID()
    private static let mediaId1 = UUID()
    private static let mediaId2 = UUID()

    private static var farm: FarmWithCrops {
        FarmWithCrops(farmId: farmId, farmName: "뒷밭", crops: [ActiveCrop(id: cropId, name: "상추")])
    }

    /// 상세 응답 원문을 그대로 흉내낸 프리필 — 기존 사진 2장, 물주기 상세.
    private static var editPrefill: VoiceRecordPrefill {
        var prefill = VoiceRecordPrefill(
            farmId: farmId,
            cropId: cropId,
            workType: .watering,
            workedAt: Date(timeIntervalSince1970: 1_784_100_000),
            memo: "상추에 물을 흠뻑 줬다. 저번보다 양을 늘렸고 흙 상태도 함께 확인했다.",
            irrigationAmount: .normal,
            irrigationMethod: .drip
        )
        prefill.existingPhotos = [
            ExistingPhoto(mediaId: mediaId1, url: "https://example.com/1.jpg"),
            ExistingPhoto(mediaId: mediaId2, url: "https://example.com/2.jpg")
        ]
        return prefill
    }

    private func makeViewModel(
        prefill: VoiceRecordPrefill?,
        saver: any RecordSaver,
        farms: [FarmWithCrops] = [farm]
    ) -> RecordComposeViewModel {
        RecordComposeViewModel(
            repository: StubRecordRepository(farms: farms),
            weatherRepository: StubWeatherRepository(
                weather: CurrentWeather(
                    temperature: 20, condition: WeatherCondition(code: "CLEAR", text: "맑음"),
                    minTemperature: nil, maxTemperature: nil
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

    // MARK: - toEditPrefill() 매핑

    @Test("상세 응답 → 수정 프리필: farmId/cropId/작업 상세/사진 mediaId가 원본 그대로 보존된다")
    func detailResponseMapsToEditPrefill() throws {
        let json = """
        {"id":"\(Self.recordId)","farmId":"\(Self.farmId)","farmName":"뒷밭",
         "cropId":"\(Self.cropId)","cropName":"상추","workType":"WATERING",
         "workedAt":"2026-07-15T09:00:00","memo":"상추에 물을 흠뻑 줬다.","weatherCondition":"맑음","weatherTemperature":20,
         "watering":{"irrigationAmount":"NORMAL","irrigationMethod":"DRIP"},
         "images":[{"mediaId":"\(Self.mediaId1)","url":"https://example.com/1.jpg"},
                   {"mediaId":"\(Self.mediaId2)","url":"https://example.com/2.jpg"}]}
        """
        let dto = try JSONDecoder().decode(RecordDetailResponseDTO.self, from: Data(json.utf8))

        let prefill = dto.toEditPrefill()

        #expect(prefill.farmId == Self.farmId)
        #expect(prefill.cropId == Self.cropId)
        #expect(prefill.workType == .watering)
        #expect(prefill.memo == "상추에 물을 흠뻑 줬다.")
        #expect(prefill.irrigationAmount == .normal)
        #expect(prefill.irrigationMethod == .drip)
        #expect(prefill.existingPhotos == [
            ExistingPhoto(mediaId: Self.mediaId1, url: "https://example.com/1.jpg"),
            ExistingPhoto(mediaId: Self.mediaId2, url: "https://example.com/2.jpg")
        ])
    }

    @Test("병해충 상세: pesticideId/pestId가 온전한 Pesticide/Pest로 복원된다")
    func pestControlIdsRoundTrip() throws {
        let pesticideId = UUID()
        let pestId = UUID()
        let json = """
        {"id":"\(Self.recordId)","farmId":"\(Self.farmId)","farmName":"뒷밭",
         "cropId":"\(Self.cropId)","cropName":"상추","workType":"PEST_CONTROL",
         "workedAt":"2026-07-15T09:00:00","memo":null,"weatherCondition":"맑음","weatherTemperature":20,
         "pestControl":{"pesticideId":"\(pesticideId)","pesticideName":"신의한수농약","pestId":"\(pestId)",
         "pestName":"짱큰나방","pesticideAmount":20,"pesticideAmountUnit":"ML","totalSprayAmount":150,"totalSprayAmountUnit":"ML"}}
        """
        let dto = try JSONDecoder().decode(RecordDetailResponseDTO.self, from: Data(json.utf8))

        let prefill = dto.toEditPrefill()

        #expect(prefill.pesticide == Pesticide(id: pesticideId, itemName: "신의한수농약", brandName: ""))
        #expect(prefill.pest == Pest(id: pestId, name: "짱큰나방"))
        #expect(prefill.pesticideAmount == 20)
        #expect(prefill.totalSprayAmount == 150)
    }

    // MARK: - UpdateRecordSaver 라우팅

    @Test("UpdateRecordSaver는 create가 아니라 updateRecord(id:_:)로 라우팅되고 entryMode는 MANUAL 고정이다")
    func updateSaverRoutesToUpdate() async {
        let repository = SpyUpdateRepository()
        let saver = UpdateRecordSaver(repository: repository, recordId: Self.recordId)
        let vm = makeViewModel(prefill: Self.editPrefill, saver: saver)

        await vm.onAppear()
        await waitUntil { vm.weather != nil }

        await vm.submit()

        let updateCalls = await repository.updateCalls
        let createCount = await repository.createCallCount
        #expect(updateCalls.count == 1)
        #expect(updateCalls.first?.id == Self.recordId)
        #expect(updateCalls.first?.request.entryMode == "MANUAL")
        #expect(createCount == 0)
        #expect(vm.createdRecordId == Self.recordId)
    }

    // MARK: - 사진 보존 / 삭제

    @Test("수정 진입 시 기존 사진이 이미 업로드된 상태로 채워지고, 재제출 시 mediaId가 그대로 보존된다")
    func existingPhotosPreservedOnSubmit() async {
        let repository = SpyUpdateRepository()
        let saver = UpdateRecordSaver(repository: repository, recordId: Self.recordId)
        let vm = makeViewModel(prefill: Self.editPrefill, saver: saver)

        await vm.onAppear()
        #expect(vm.attachments.count == 2)
        #expect(vm.attachments.allSatisfy { !$0.isUploading })
        #expect(Set(vm.mediaIds) == Set([Self.mediaId1, Self.mediaId2]))

        await waitUntil { vm.weather != nil }
        await vm.submit()

        let saved = await repository.updateCalls.first?.request
        #expect(Set(saved?.mediaIds ?? []) == Set([Self.mediaId1, Self.mediaId2]))
    }

    @Test("기존 사진 한 장을 삭제하면 재제출 페이로드의 mediaIds에서 빠진다")
    func removedPhotoDropsFromPayload() async {
        let repository = SpyUpdateRepository()
        let saver = UpdateRecordSaver(repository: repository, recordId: Self.recordId)
        let vm = makeViewModel(prefill: Self.editPrefill, saver: saver)

        await vm.onAppear()
        vm.removeImage(id: Self.mediaId1)
        await waitUntil { vm.weather != nil }

        await vm.submit()

        let saved = await repository.updateCalls.first?.request
        #expect(saved?.mediaIds == [Self.mediaId2])
    }
}
