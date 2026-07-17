//
//  CommunityComposeViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Drives the "게시물 작성하기" screen: crop board selection, title/body, the Q&A toggle, and image
/// attachments. Images are uploaded as they're picked so submit only sends the resulting `mediaId`s.
@MainActor
@Observable
final class CommunityComposeViewModel {
    /// One attached image. `id` is a local temp id (stable while uploading); `mediaId` is filled once the
    /// upload succeeds and is what gets sent on submit. `isUploading` drives the per-slot spinner.
    struct Attachment: Identifiable, Sendable {
        let id: UUID
        let previewData: Data
        var mediaId: UUID?
        var isUploading: Bool
    }

    static let titleLimit = 30
    static let bodyLimit = 500
    static let maxImages = 5

    /// Crop boards offered as chips. Seeded from the member's boards; the picker can add more.
    private(set) var boards: [CommunityBoard] = []
    private(set) var selectedCropId: UUID?

    var title: String = ""
    var body: String = ""
    /// Q&A 토글 on → `.question`, off → `.general`.
    var isQuestion: Bool = false

    private(set) var attachments: [Attachment] = []
    private(set) var isSubmitting = false
    private(set) var errorMessage: String?

    /// Recently-created 영농일지, offered as quick picks in the compose form without opening the full picker.
    private(set) var recentRecords: [FarmingRecordSummary] = []
    private(set) var selectedFarmingRecord: FarmingRecordSummary?

    private let repository: any CommunityRepository
    private let cropCatalog: any CropCatalogService
    private let mediaRepository: any MediaUploadRepository
    private let recordRepository: any RecordRepository

    init(
        repository: any CommunityRepository,
        cropCatalog: any CropCatalogService,
        mediaRepository: any MediaUploadRepository,
        recordRepository: any RecordRepository
    ) {
        self.repository = repository
        self.cropCatalog = cropCatalog
        self.mediaRepository = mediaRepository
        self.recordRepository = recordRepository
    }

    /// True while any attachment is still uploading — submit waits for these so no `mediaId` is missed.
    var isUploadingImage: Bool { attachments.contains(where: \.isUploading) }

    var isTitleOverLimit: Bool {
        title.count > Self.titleLimit
    }

    var isBodyOverLimit: Bool {
        body.count > Self.bodyLimit
    }

    var inputValidationMessage: String? {
        if isTitleOverLimit {
            return "제목은 최대 30자까지 입력 가능합니다."
        }
        if isBodyOverLimit {
            return "내용은 최대 500자까지 입력 가능합니다."
        }
        return nil
    }

    var canSubmit: Bool {
        selectedCropId != nil
            && !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !isTitleOverLimit
            && !isBodyOverLimit
            && !isSubmitting
            && !isUploadingImage
    }

    var canAddImage: Bool { attachments.count < Self.maxImages }

    func loadBoards() async {
        guard boards.isEmpty else { return }
        guard let serverBoards = try? await repository.fetchBoards() else { return }
        boards = serverBoards
    }

    func selectCrop(_ cropId: UUID) {
        selectedCropId = cropId
    }

    // MARK: - Farming record

    func loadRecentRecords() async {
        guard recentRecords.isEmpty else { return }
        guard let page = try? await recordRepository.fetchRecords(RecordQuery()) else { return }
        recentRecords = page.items
    }

    func selectFarmingRecord(_ record: FarmingRecordSummary?) {
        selectedFarmingRecord = record
    }

    // MARK: - Board picker (작물 추가)

    func catalogCrops() async -> [Crop] {
        (try? await cropCatalog.fetchCrops()) ?? []
    }

    func catalogCategories() async -> [CropCategory] {
        (try? await cropCatalog.fetchCategories()) ?? []
    }

    /// Adds picker-selected crops as chips (de-duped) and selects the first newly added one.
    func addBoards(from crops: [Crop]) {
        guard !crops.isEmpty else { return }
        var existing = Set(boards.map(\.cropId))
        for crop in crops where existing.insert(crop.id).inserted {
            boards.append(CommunityBoard(cropId: crop.id, cropName: crop.name))
        }
        selectedCropId = crops.first?.id
    }

    // MARK: - Images

    func addImage(_ data: Data) async {
        guard canAddImage else { return }
        // Show the picked image immediately with a spinner, then fill in the mediaId once uploaded.
        let tempId = UUID()
        attachments.append(Attachment(id: tempId, previewData: data, mediaId: nil, isUploading: true))
        do {
            let uploaded = try await mediaRepository.uploadCommunityImage(data, originalFilename: nil)
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

    // MARK: - Submit

    /// Returns the new post id on success, or nil on failure (with `errorMessage` set).
    func submit() async -> UUID? {
        guard let cropId = selectedCropId, canSubmit else { return nil }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            return try await repository.createPost(
                cropId: cropId,
                postType: isQuestion ? .question : .general,
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                body: body.trimmingCharacters(in: .whitespacesAndNewlines),
                farmingRecordId: selectedFarmingRecord?.id,
                mediaIds: attachments.compactMap(\.mediaId)
            )
        } catch {
            errorMessage = CommunityErrorMessage.text(for: error)
            return nil
        }
    }
}
