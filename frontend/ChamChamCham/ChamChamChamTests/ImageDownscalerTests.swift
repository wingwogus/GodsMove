//
//  ImageDownscalerTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
import Testing
import UIKit
@testable import ChamChamCham

@Suite("ImageDownscaler")
struct ImageDownscalerTests {

    private func solidImage(width: CGFloat, height: CGFloat) -> Data {
        // scale = 1 so the rendered pixel dimensions equal the requested size regardless of the simulator's
        // screen scale — otherwise a "200pt" image decodes back as 200×(screen scale) pixels.
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        let image = renderer.image { context in
            UIColor.systemGreen.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return image.jpegData(compressionQuality: 1.0)!
    }

    @Test("caps the longest edge at maxDimension for oversized images")
    func downscalesOversizedImage() throws {
        let data = solidImage(width: 2000, height: 1500)

        let result = try #require(ImageDownscaler.downscaledJPEGData(from: data, maxDimension: 1024))
        let resized = try #require(UIImage(data: result))

        #expect(max(resized.size.width, resized.size.height) == 1024)
        // Aspect ratio preserved: 2000x1500 -> 1024x768.
        #expect(Int(resized.size.height.rounded()) == 768)
    }

    @Test("leaves images already within bounds at their original size")
    func passesThroughSmallImage() throws {
        let data = solidImage(width: 200, height: 200)

        let result = try #require(ImageDownscaler.downscaledJPEGData(from: data, maxDimension: 1024))
        let resized = try #require(UIImage(data: result))

        #expect(resized.size.width == 200)
        #expect(resized.size.height == 200)
    }

    @Test("returns nil for data that is not an image")
    func returnsNilForNonImageData() {
        let garbage = Data("not an image".utf8)

        #expect(ImageDownscaler.downscaledJPEGData(from: garbage) == nil)
    }
}
