//
//  RecordComposeViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

/// Drives the 텍스트 기록 작성(생성) form. Targets the origin-dev `SaveRecordRequest` contract (see
/// `docs/figma/record/2026-07-13-record-backend-conflicts.md`). Weather is auto-fetched per farm; photos are
/// uploaded to media ids on pick; submit builds the DTO for `POST /farming-records`.
@MainActor
@Observable
final class RecordComposeViewModel {
    // MARK: 공통
    var date: Date = Date()
    var selectedFarmId: UUID? { didSet { onFarmChanged() } }
    var selectedCropId: UUID?
    var workType: WorkType?
    var memo: String = ""

    private(set) var farms: [FarmWithCrops] = []
    private(set) var weather: CurrentWeather?
    private(set) var isLoadingWeather = false

    // MARK: 사진 (0~5)
    private(set) var mediaIds: [UUID] = []
    private(set) var isUploadingImage = false

    // MARK: 심기
    var plantingMethod: PlantingMethod?
    var seedAmount: String = ""
    var seedlingCount: String = ""
    var propagationMethod: PropagationMethod?

    // MARK: 물주기
    var irrigationMethod: IrrigationMethod?
    var irrigationAmount: IrrigationAmount?

    // MARK: 비료 주기 (사용 비료는 카탈로그 API 부재 → 자유 입력)
    var fertilizerMaterialName: String = ""
    var fertilizerAmount: String = ""
    var fertilizerAmountUnit: FertilizerAmountUnit = .g
    var fertilizingMethod: FertilizingMethod?

    // MARK: 병해충 관리
    var selectedPesticide: Pesticide?
    var pesticideAmount: String = ""
    var pesticideAmountUnit: PesticideAmountUnit = .ml
    var totalSprayAmount: String = ""
    var selectedPest: Pest?
    private(set) var pests: [Pest] = []

    // MARK: 잡초 관리
    var weedingMethod: WeedingMethod?

    // MARK: 수확
    var growthPeriod: String = ""
    var growthPeriodUnit: GrowthPeriodUnit = .month
    var harvestAmount: String = ""
    var harvestAmountUnknown: Bool = false
    var medicinalPart: MedicinalPart?
    var isLastHarvest: Bool = false

    // MARK: 상태
    var showValidation = false
    private(set) var isSubmitting = false
    private(set) var errorMessage: String?
    private(set) var createdRecordId: UUID?

    private let repository: any RecordRepository
    private let mediaUpload: any MediaUploadRepository

    init(repository: any RecordRepository, mediaUpload: any MediaUploadRepository) {
        self.repository = repository
        self.mediaUpload = mediaUpload
    }

    var crops: [ActiveCrop] {
        farms.first { $0.farmId == selectedFarmId }?.crops ?? []
    }

    var canAddMorePhotos: Bool { mediaIds.count < 5 }

    // MARK: - Load

    func onAppear() async {
        guard farms.isEmpty else { return }
        farms = (try? await repository.fetchFarmCrops()) ?? []
    }

    private func onFarmChanged() {
        // 농지 변경 시 그 농지에 없는 작물 선택 해제 + 날씨 재조회.
        if let cropId = selectedCropId, !crops.contains(where: { $0.id == cropId }) {
            selectedCropId = nil
        }
        guard let farmId = selectedFarmId else { weather = nil; return }
        Task { await loadWeather(farmId: farmId) }
    }

    private func loadWeather(farmId: UUID) async {
        isLoadingWeather = true
        defer { isLoadingWeather = false }
        weather = try? await repository.fetchWeather(farmId: farmId)
    }

    // MARK: - 병해충: 농약 선택 시 대상 병해충 목록 로드

    func selectPesticide(_ pesticide: Pesticide) async {
        selectedPesticide = pesticide
        selectedPest = nil
        pests = (try? await repository.fetchPests(pesticideId: pesticide.id)) ?? []
    }

    func searchPesticides(keyword: String?) async -> [Pesticide] {
        (try? await repository.searchPesticides(keyword: keyword)) ?? []
    }

    // MARK: - 사진 업로드

    func addImage(_ data: Data) async {
        guard canAddMorePhotos else { return }
        isUploadingImage = true
        defer { isUploadingImage = false }
        if let uploaded = try? await mediaUpload.uploadFarmingRecordImage(data, originalFilename: nil) {
            mediaIds.append(uploaded.mediaId)
        }
    }

    func removeImage(at index: Int) {
        guard mediaIds.indices.contains(index) else { return }
        mediaIds.remove(at: index)
    }

    // MARK: - 검증

    var memoError: String? {
        let trimmed = memo.trimmingCharacters(in: .whitespacesAndNewlines)
        if memo.count > 500 { return "작업 내용은 최대 500자까지 작성 가능합니다." }
        if trimmed.isEmpty { return "작업 내용은 필수로 작성해주세요." }
        if trimmed.count < 30 { return "작업 내용은 30자 이상 작성해주세요." }
        return nil
    }

    /// 진행 작물 영역 조합 문구(농지+작물). nil이면 유효.
    var farmCropError: String? {
        switch (selectedFarmId == nil, selectedCropId == nil) {
        case (true, true): "농지와 작물은 필수로 선택해주세요."
        case (true, false): "농지는 필수로 선택해주세요."
        case (false, true): "작물은 필수로 선택해주세요."
        case (false, false): nil
        }
    }

    var workTypeError: String? { workType == nil ? "작업 내용은 필수로 선택해주세요." : nil }

    /// 선택된 workType 상세의 필수값 충족 여부(제출 가능성 판단용, 문구는 각 필드에서).
    var detailValid: Bool {
        guard let workType else { return false }
        switch workType {
        case .planting:
            guard let plantingMethod else { return false }
            switch plantingMethod {
            case .seed: return doubleValue(seedAmount) != nil
            case .seedling: return intValue(seedlingCount) != nil
            }
        case .fertilizing:
            return !fertilizerMaterialName.trimmingCharacters(in: .whitespaces).isEmpty
                && doubleValue(fertilizerAmount) != nil
        case .pestControl:
            return selectedPesticide != nil
                && doubleValue(pesticideAmount) != nil
                && doubleValue(totalSprayAmount) != nil
        case .harvest:
            return true // isLastHarvest는 Bool(기본 false)로 항상 값 존재
        case .watering, .weeding, .pruning, .etc:
            return true
        }
    }

    var canSubmit: Bool {
        farmCropError == nil && workTypeError == nil && memoError == nil
            && weather != nil && detailValid && !isSubmitting
    }

    // MARK: - 제출

    func submit() async {
        showValidation = true
        guard canSubmit, let request = buildRequest() else { return }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            createdRecordId = try await repository.createRecord(request)
        } catch {
            errorMessage = RecordErrorMessage.text(for: error)
        }
    }

    private func buildRequest() -> SaveRecordRequestDTO? {
        guard let farmId = selectedFarmId, let cropId = selectedCropId,
              let workType, let weather else { return nil }
        return SaveRecordRequestDTO(
            farmId: farmId,
            cropId: cropId,
            workType: workType.rawValue,
            workedAt: Self.dateTimeFormatter.string(from: date),
            weatherCondition: weather.condition,
            weatherTemperature: weather.temperature,
            memo: memo,
            planting: workType == .planting ? plantingDetail() : nil,
            watering: workType == .watering ? wateringDetail() : nil,
            fertilizing: workType == .fertilizing ? fertilizingDetail() : nil,
            pestControl: workType == .pestControl ? pestControlDetail() : nil,
            weeding: workType == .weeding ? weedingDetail() : nil,
            harvest: workType == .harvest ? harvestDetail() : nil,
            mediaIds: mediaIds,
            entryMode: EntryMode.manual.rawValue
        )
    }

    private func plantingDetail() -> PlantingDetailRequestDTO? {
        guard let plantingMethod else { return nil }
        let isSeed = plantingMethod == .seed
        return PlantingDetailRequestDTO(
            plantingMethod: plantingMethod.rawValue,
            seedAmount: isSeed ? doubleValue(seedAmount) : nil,
            seedAmountUnit: isSeed ? SeedAmountUnit.g.rawValue : nil,
            seedlingCount: isSeed ? nil : intValue(seedlingCount),
            seedlingUnit: isSeed ? nil : SeedlingUnit.ju.rawValue,
            propagationMethod: isSeed ? nil : propagationMethod?.rawValue
        )
    }

    private func wateringDetail() -> WateringDetailRequestDTO {
        WateringDetailRequestDTO(
            irrigationAmount: irrigationAmount?.rawValue,
            irrigationMethod: irrigationMethod?.rawValue
        )
    }

    private func fertilizingDetail() -> FertilizingDetailRequestDTO? {
        guard let amount = doubleValue(fertilizerAmount) else { return nil }
        return FertilizingDetailRequestDTO(
            materialName: fertilizerMaterialName,
            amount: amount,
            amountUnit: fertilizerAmountUnit.rawValue,
            applicationMethod: fertilizingMethod?.rawValue
        )
    }

    private func pestControlDetail() -> PestControlDetailRequestDTO? {
        guard let pesticide = selectedPesticide,
              let amount = doubleValue(pesticideAmount),
              let spray = doubleValue(totalSprayAmount) else { return nil }
        return PestControlDetailRequestDTO(
            pesticideId: pesticide.id,
            pesticideAmount: amount,
            pesticideAmountUnit: pesticideAmountUnit.rawValue,
            totalSprayAmount: spray,
            totalSprayAmountUnit: SprayAmountUnit.l.rawValue,
            pestId: selectedPest?.id
        )
    }

    private func weedingDetail() -> WeedingDetailRequestDTO {
        WeedingDetailRequestDTO(weedingMethod: weedingMethod?.rawValue)
    }

    private func harvestDetail() -> HarvestDetailRequestDTO {
        HarvestDetailRequestDTO(
            harvestAmount: harvestAmountUnknown ? nil : doubleValue(harvestAmount),
            harvestAmountUnknown: harvestAmountUnknown,
            medicinalPart: medicinalPart?.rawValue,
            harvestSource: "CULTIVATED",
            growthPeriod: intValue(growthPeriod),
            growthPeriodUnit: intValue(growthPeriod) != nil ? growthPeriodUnit.rawValue : nil,
            isLastHarvest: isLastHarvest
        )
    }

    // MARK: - Helpers

    private func doubleValue(_ s: String) -> Double? {
        Double(s.trimmingCharacters(in: .whitespaces))
    }

    private func intValue(_ s: String) -> Int? {
        Int(s.trimmingCharacters(in: .whitespaces))
    }

    private static let dateTimeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return f
    }()
}
