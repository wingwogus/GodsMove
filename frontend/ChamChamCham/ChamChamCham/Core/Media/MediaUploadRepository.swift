//
//  MediaUploadRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

protocol MediaUploadRepository: Sendable {
    /// Uploads a profile photo and returns the media record. Callers persist `mediaId` (as `profileMediaId`) so a
    /// later onboarding-complete retry does not re-upload the same image.
    func uploadProfileImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO

    /// Uploads a community post/comment image and returns the media record. Callers collect the returned
    /// `mediaId`s and pass them as `mediaIds`/`mediaId` when saving the post or comment.
    func uploadCommunityImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO

    /// Uploads a farming-record image and returns the media record.
    func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO
}

struct RemoteMediaUploadRepository: MediaUploadRepository {
    let apiClient: APIClient

    func uploadProfileImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        try await upload(imageData, usage: .profile, originalFilename: originalFilename)
    }

    func uploadCommunityImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        try await upload(imageData, usage: .communityPost, originalFilename: originalFilename)
    }

    func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        try await upload(imageData, usage: .farmingRecord, originalFilename: originalFilename)
    }

    private func upload(
        _ imageData: Data,
        usage: MediaImageUsage,
        originalFilename: String?
    ) async throws -> UploadedImageResponseDTO {
        let jpeg = ImageDownscaler.downscaledJPEGData(from: imageData) ?? imageData
        let requestDTO = UploadImageRequestDTO(
            usageType: usage.rawValue,
            base64Image: jpeg.base64EncodedString(),
            originalFilename: originalFilename,
            contentType: "image/jpeg"
        )
        return try await apiClient.send(MediaEndpoint.uploadImage(requestDTO))
    }
}
