//
//  ImageDownscaler.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import UIKit

/// Shrinks a picked photo before it becomes a Base64 JSON payload. A full-resolution phone photo Base64-encoded is
/// several MB of text — on the poor rural connectivity this app targets that's a needless upload failure. Capping the
/// longest edge and re-encoding as JPEG keeps profile avatars small without a visible quality loss at display size.
enum ImageDownscaler {
    static func downscaledJPEGData(
        from data: Data,
        maxDimension: CGFloat = 1024,
        compressionQuality: CGFloat = 0.8
    ) -> Data? {
        guard let image = UIImage(data: data) else { return nil }

        let longestEdge = max(image.size.width, image.size.height)
        guard longestEdge > maxDimension else {
            return image.jpegData(compressionQuality: compressionQuality)
        }

        let scale = maxDimension / longestEdge
        let targetSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
        return resized.jpegData(compressionQuality: compressionQuality)
    }
}
