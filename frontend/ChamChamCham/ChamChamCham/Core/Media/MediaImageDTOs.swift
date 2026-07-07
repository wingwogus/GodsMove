//
//  MediaImageDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Server-side `UploadedMediaUsageType`. `.profile` backs onboarding avatars; `.communityPost` backs post and
/// comment image attachments (see `MediaUploadRepository.uploadCommunityImage`).
enum MediaImageUsage: String, Sendable {
    case profile = "PROFILE"
    case communityPost = "COMMUNITY_POST"
}

/// Body for `POST /api/v1/media/images`. The backend takes a Base64 payload as JSON (not multipart), so this rides
/// the existing JSON `APIClient` with no special encoding.
struct UploadImageRequestDTO: Encodable, Sendable {
    let usageType: String
    let base64Image: String
    let originalFilename: String?
    let contentType: String?
}

/// Response of `POST /api/v1/media/images`. `status` (TEMP/ATTACHED/DELETED) is decoded as a plain `String` on
/// purpose — nothing client-side consumes it, so a new server enum case must not break decoding.
struct UploadedImageResponseDTO: Decodable, Sendable {
    let mediaId: UUID
    let imageUrl: String
    let status: String
}
