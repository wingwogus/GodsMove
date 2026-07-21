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
    /// 날씨 조회가 실패로 끝났는지. 로드 중/성공이면 false. 재시도 어포던스 노출용.
    private(set) var weatherLoadFailed = false
    /// 농지를 빠르게 바꿀 때 이전 요청의 늦은 응답이 최신 상태를 덮어쓰지 않게 하는 세대 토큰.
    private var weatherLoadToken = 0

    // MARK: 사진 (0~5)
    /// 첨부 사진 한 장의 미리보기 원본. 새로 고른 사진은 로컬 `Data`, 수정 진입 시 프리필로 채워진
    /// 기존 사진은 서버 URL — 둘 다 같은 슬롯 UI(`AppImageUploadSlot`)로 그려진다.
    enum AttachmentSource: Sendable {
        case local(Data)
        case remote(url: String)
    }

    /// 첨부 사진 한 장. `id`는 업로드 중에도 안정적인 로컬 임시 id이며, 업로드가 끝나면
    /// `mediaId`가 채워져 제출 시 사용된다. 기존 사진(`source == .remote`)은 이미 `mediaId`를
    /// 가진 채로 시작해 재업로드 없이 그대로 재제출된다. `isUploading`이 슬롯별 스피너를 구동한다.
    struct Attachment: Identifiable, Sendable {
        let id: UUID
        let source: AttachmentSource
        var mediaId: UUID?
        var isUploading: Bool
    }

    private(set) var attachments: [Attachment] = []
    var isUploadingImage: Bool { attachments.contains(where: \.isUploading) }
    var mediaIds: [UUID] { attachments.compactMap(\.mediaId) }

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
    var harvestAmount: String = "" {
        didSet {
            guard !harvestAmount.isEmpty, harvestAmountUnknown else { return }
            harvestAmountUnknown = false
        }
    }
    var harvestAmountUnknown: Bool = false {
        didSet {
            guard harvestAmountUnknown, !harvestAmount.isEmpty else { return }
            harvestAmount = ""
        }
    }
    var medicinalPart: MedicinalPart?
    var isLastHarvest: Bool = false

    // MARK: 상태
    var showValidation = false
    private(set) var isSubmitting = false
    private(set) var errorMessage: String?
    private(set) var createdRecordId: UUID?
    /// 음성 검토 전용: confirm이 VOICE_002(이미 처리됨)로 거부된 상태. 재시도 금지 —
    /// 뷰가 onSessionInvalid로 플로우를 닫는다.
    private(set) var voiceSessionInvalidated = false

    private let repository: any RecordRepository
    private let weatherRepository: any WeatherRepository
    private let mediaUpload: any MediaUploadRepository
    private let saver: any RecordSaver
    private let prefill: VoiceRecordPrefill?

    init(
        repository: any RecordRepository,
        weatherRepository: any WeatherRepository,
        mediaUpload: any MediaUploadRepository,
        saver: (any RecordSaver)? = nil,
        prefill: VoiceRecordPrefill? = nil
    ) {
        self.repository = repository
        self.weatherRepository = weatherRepository
        self.mediaUpload = mediaUpload
        self.saver = saver ?? CreateRecordSaver(repository: repository)
        self.prefill = prefill
    }

    var crops: [ActiveCrop] {
        farms.first { $0.farmId == selectedFarmId }?.crops ?? []
    }

    var canAddMorePhotos: Bool { attachments.count < 5 }

    // MARK: - Load

    func onAppear() async {
        guard farms.isEmpty else { return }
        farms = await loadFarmCrops()
        if let prefill {
            applyPrefill(prefill)
        }
        // 농지가 하나뿐이면 자동 선택 → 진입 즉시 날씨 조회(불필요한 탭 제거). didSet이 loadWeather 트리거.
        if selectedFarmId == nil, farms.count == 1 {
            selectedFarmId = farms[0].farmId
        }
    }

    /// 농지 목록은 음성 후보의 농지·작물 매칭에 필요하다. 일시적 네트워크 오류로 목록이 비면
    /// 음성으로 잡은 농지·작물이 조용히 유실되므로, 조회가 실패(throw)하면 한 번 재시도한다.
    /// 성공한 빈 목록(등록 농지 없음)은 재시도하지 않는다.
    private func loadFarmCrops() async -> [FarmWithCrops] {
        if let farms = try? await repository.fetchFarmCrops() {
            return farms
        }
        return (try? await repository.fetchFarmCrops()) ?? []
    }

    /// 음성 후보(AI 초안)를 폼에 채운다. farms 로드 뒤에 불러야 farmId 설정의 didSet이
    /// 작물 정리·날씨 조회까지 이어진다. 실소유가 아닌 farm/crop id는 버린다(검증 문구가 안내).
    private func applyPrefill(_ prefill: VoiceRecordPrefill) {
        if let farmId = prefill.farmId, farms.contains(where: { $0.farmId == farmId }) {
            selectedFarmId = farmId
        }
        if let cropId = prefill.cropId, crops.contains(where: { $0.id == cropId }) {
            selectedCropId = cropId
        }
        workType = prefill.workType
        if let workedAt = prefill.workedAt { date = workedAt }
        memo = prefill.memo ?? ""

        plantingMethod = prefill.plantingMethod
        seedAmount = Self.numberText(prefill.seedAmount)
        seedlingCount = prefill.seedlingCount.map(String.init) ?? ""
        propagationMethod = prefill.propagationMethod

        irrigationAmount = prefill.irrigationAmount
        irrigationMethod = prefill.irrigationMethod

        fertilizerMaterialName = prefill.fertilizerMaterialName ?? ""
        fertilizerAmount = Self.numberText(prefill.fertilizerAmount)
        if let unit = prefill.fertilizerAmountUnit { fertilizerAmountUnit = unit }
        fertilizingMethod = prefill.fertilizingMethod

        if let pesticide = prefill.pesticide {
            selectedPesticide = pesticide
            selectedPest = prefill.pest
            Task { pests = (try? await repository.fetchPests(pesticideId: pesticide.id)) ?? [] }
        }
        pesticideAmount = Self.numberText(prefill.pesticideAmount)
        if let unit = prefill.pesticideAmountUnit { pesticideAmountUnit = unit }
        totalSprayAmount = Self.numberText(prefill.totalSprayAmount)

        weedingMethod = prefill.weedingMethod

        growthPeriod = prefill.growthPeriod.map(String.init) ?? ""
        harvestAmount = Self.numberText(prefill.harvestAmount)
        harvestAmountUnknown = prefill.harvestAmountUnknown
        medicinalPart = prefill.medicinalPart
        isLastHarvest = prefill.isLastHarvest

        // 서버가 알려준 누락 필드는 진입 즉시 기존 필수값 문구로 표시한다 (BR-EXCEPTION-005).
        if !prefill.missingFields.isEmpty {
            showValidation = true
        }

        // 수정 진입: 기존 사진을 이미 업로드된 상태로 채운다 — 재업로드 없이 mediaId를 그대로 재제출.
        attachments = prefill.existingPhotos.map {
            Attachment(id: $0.mediaId, source: .remote(url: $0.url), mediaId: $0.mediaId, isUploading: false)
        }
    }

    /// 정수로 떨어지는 값은 "30.0"이 아니라 "30"으로 채운다.
    private static func numberText(_ value: Double?) -> String {
        guard let value else { return "" }
        return value.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(value))
            : String(value)
    }

    private func onFarmChanged() {
        // 농지 변경 시 그 농지에 없는 작물 선택 해제 + 날씨 재조회.
        if let cropId = selectedCropId, !crops.contains(where: { $0.id == cropId }) {
            selectedCropId = nil
        }
        // 작물이 하나뿐이면 자동 선택.
        if selectedCropId == nil, crops.count == 1 {
            selectedCropId = crops[0].id
        }
        guard let farmId = selectedFarmId else {
            weather = nil
            weatherLoadFailed = false
            return
        }
        Task { await loadWeather(farmId: farmId) }
    }

    /// 날씨 조회 실패 후 사용자가 재시도할 때 호출.
    func retryWeather() async {
        guard let farmId = selectedFarmId else { return }
        await loadWeather(farmId: farmId)
    }

    private func loadWeather(farmId: UUID) async {
        weatherLoadToken &+= 1
        let token = weatherLoadToken
        weather = nil
        weatherLoadFailed = false
        isLoadingWeather = true
        do {
            let result = try await weatherRepository.fetchHome(farmId: farmId)
            guard token == weatherLoadToken else { return } // 더 최근 선택이 있으면 무시
            weather = result
        } catch {
            guard token == weatherLoadToken else { return }
            weatherLoadFailed = true
        }
        if token == weatherLoadToken { isLoadingWeather = false }
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
        // 고른 사진을 스피너와 함께 즉시 보여주고, 업로드가 끝나면 mediaId를 채운다.
        let tempId = UUID()
        attachments.append(Attachment(id: tempId, source: .local(data), mediaId: nil, isUploading: true))
        do {
            let uploaded = try await mediaUpload.uploadFarmingRecordImage(data, originalFilename: nil)
            guard let index = attachments.firstIndex(where: { $0.id == tempId }) else { return }
            attachments[index].mediaId = uploaded.mediaId
            attachments[index].isUploading = false
        } catch {
            attachments.removeAll { $0.id == tempId }
            errorMessage = "사진을 올리지 못했어요. 다시 시도해주세요."
        }
    }

    func removeImage(id: UUID) {
        attachments.removeAll { $0.id == id }
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

    /// 농지는 선택됐지만 날씨를 못 불러온 상태. 농지 미선택은 farmCropError가 담당하므로 여기선 제외.
    var weatherError: String? {
        guard selectedFarmId != nil, !isLoadingWeather, weather == nil else { return nil }
        return "날씨 정보를 불러오지 못했어요. 날씨를 눌러 다시 시도해주세요."
    }

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
            && weather != nil && detailValid && !isSubmitting && !isUploadingImage
    }

    // MARK: - 제출

    func submit() async {
        showValidation = true
        guard canSubmit, let request = buildRequest() else { return }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            createdRecordId = try await saver.save(request)
        } catch VoiceSessionError.alreadyProcessed {
            voiceSessionInvalidated = true
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
            weatherCondition: weather.condition.text,
            weatherTemperature: weather.temperature,
            memo: memo,
            planting: workType == .planting ? plantingDetail() : nil,
            watering: workType == .watering ? wateringDetail() : nil,
            fertilizing: workType == .fertilizing ? fertilizingDetail() : nil,
            pestControl: workType == .pestControl ? pestControlDetail() : nil,
            weeding: workType == .weeding ? weedingDetail() : nil,
            harvest: workType == .harvest ? harvestDetail() : nil,
            mediaIds: mediaIds,
            entryMode: saver.entryMode.rawValue
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
            totalSprayAmountUnit: SprayAmountUnit.ml.rawValue,
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
