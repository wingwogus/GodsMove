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
    /// One attached image: `id` is the server `mediaId` (also used as `mediaIds` on submit); `previewData`
    /// is the local JPEG for the thumbnail.
    struct Attachment: Identifiable, Sendable {
        let id: UUID
        let previewData: Data
    }

    static let titleLimit = 50
    static let maxImages = 5

    /// Crop boards offered as chips. Seeded from the member's boards; the picker can add more.
    private(set) var boards: [CommunityBoard] = []
    private(set) var selectedCropId: UUID?

    var title: String = ""
    var body: String = ""
    /// Q&A 토글 on → `.question`, off → `.general`.
    var isQuestion: Bool = false

    private(set) var attachments: [Attachment] = []
    private(set) var isUploadingImage = false
    private(set) var isSubmitting = false
    private(set) var errorMessage: String?

    private let repository: any CommunityRepository
    private let cropCatalog: any CropCatalogService
    private let mediaRepository: any MediaUploadRepository

    init(
        repository: any CommunityRepository,
        cropCatalog: any CropCatalogService,
        mediaRepository: any MediaUploadRepository
    ) {
        self.repository = repository
        self.cropCatalog = cropCatalog
        self.mediaRepository = mediaRepository
    }

    var canSubmit: Bool {
        selectedCropId != nil
            && !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !isSubmitting
    }

    var canAddImage: Bool { attachments.count < Self.maxImages && !isUploadingImage }

    func loadBoards() async {
        guard boards.isEmpty else { return }
        guard let serverBoards = try? await repository.fetchBoards() else { return }
        boards = serverBoards
    }

    func selectCrop(_ cropId: UUID) {
        selectedCropId = cropId
    }

    // MARK: - Board picker (작물 추가)

    func catalogCrops() async -> [Crop] {
        (try? await cropCatalog.fetchCrops()) ?? []
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
        isUploadingImage = true
        defer { isUploadingImage = false }
        do {
            let uploaded = try await mediaRepository.uploadCommunityImage(data, originalFilename: nil)
            attachments.append(Attachment(id: uploaded.mediaId, previewData: data))
        } catch {
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
                farmingRecordId: nil,
                mediaIds: attachments.map(\.id)
            )
        } catch {
            errorMessage = CommunityErrorMessage.text(for: error)
            return nil
        }
    }
}
