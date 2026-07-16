//
//  AppFlowLayoutRenderingTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI
import Testing
@testable import ChamChamCham

/// Renders `AppFlowLayout` through a real `UIHostingController` layout pass (not just the view-model
/// logic) to confirm the ProfileMainView crop-badge truncation bug is actually fixed at the SwiftUI
/// layout level, not only in the collapse/expand state machine.
@MainActor
@Suite("AppFlowLayout wraps crop badges instead of compressing them")
struct AppFlowLayoutRenderingTests {
    /// `profileCard`'s content width on iPhone SE: screen width minus the outer `LazyVStack` padding
    /// and the card's own internal padding, both `Spacing.lg - Spacing.xs` (20pt) on each side.
    private static let profileCardContentWidth: CGFloat = 375 - 4 * (Spacing.lg - Spacing.xs)

    @Test("long crop names that don't fit one row wrap onto multiple rows instead of truncating")
    func wrapsLongLabelsOntoMultipleRows() {
        // Same names/shape as the reported screenshot: badges long enough that 5 of them can't fit in
        // one row at card width — before the fix, `HStack` compressed each `Text` (lineLimit(1)) until
        // it fit, rendering "매발톱…"/"가죽…".
        let names = ["매발톱꽃", "마타리", "멀꿀", "개별꽃", "가죽나무"]
        let singleRowHeight = badgeRowHeight(names: ["멀꿀"])

        let wrappedHeight = badgeRowHeight(names: names)

        #expect(wrappedHeight > singleRowHeight + Spacing.sm)
    }

    @Test("few short crop names stay on a single row")
    func keepsShortLabelsOnOneRow() {
        let names = ["인삼", "고추", "배추"]
        let singleRowHeight = badgeRowHeight(names: ["멀꿀"])

        let height = badgeRowHeight(names: names)

        #expect(height <= singleRowHeight + 1)
    }

    /// Hosts `AppFlowLayout(names)` at `profileCardContentWidth` through a real `UIHostingController`
    /// layout pass and returns the measured content height.
    private func badgeRowHeight(names: [String]) -> CGFloat {
        let content = AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm) {
            ForEach(names, id: \.self) { name in
                AppBadge(label: name, style: .solid, variant: .primary)
            }
        }
        let hosting = UIHostingController(rootView: content)
        let fitted = hosting.sizeThatFits(
            in: CGSize(width: Self.profileCardContentWidth, height: .greatestFiniteMagnitude)
        )
        return fitted.height
    }

    // MARK: - Alignment (Figma: "뱃지 블록은 카드 안에서 가로 중앙 정렬")

    @Test(".center alignment centers a short row; .leading starts flush left")
    func centerAlignmentCentersRowWithinTheCard() {
        let width: CGFloat = 300
        let centerMargins = contentMargins(names: ["인삼", "고추", "배추"], alignment: .center, width: width)
        let leadingMargins = contentMargins(names: ["인삼", "고추", "배추"], alignment: .leading, width: width)

        // Centered: left/right margins around the badge row should be (nearly) equal.
        #expect(abs(centerMargins.left - centerMargins.right) <= 2)
        // Leading: content starts flush against the left edge.
        #expect(leadingMargins.left <= 1)
        // The same row pushed further right under .center than under .leading proves the offset is
        // actually applied, not just a coincidence of the row happening to already be centered.
        #expect(centerMargins.left > leadingMargins.left + 10)
    }

    /// Renders `AppFlowLayout(names)` at a fixed `width` against a white background via `ImageRenderer`
    /// and measures the empty margin to the left/right of the non-white badge content by scanning the
    /// vertical midline of the rasterized pixels.
    private func contentMargins(
        names: [String],
        alignment: AppFlowLayout.RowAlignment,
        width: CGFloat
    ) -> (left: CGFloat, right: CGFloat) {
        let content = AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm, alignment: alignment) {
            ForEach(names, id: \.self) { name in
                AppBadge(label: name, style: .solid, variant: .primary)
            }
        }
        .frame(width: width, height: 40)
        .background(Color.white)

        let renderer = ImageRenderer(content: content)
        renderer.scale = 1
        guard let uiImage = renderer.uiImage, let cgImage = uiImage.cgImage else {
            Issue.record("failed to rasterize AppFlowLayout for alignment measurement")
            return (0, 0)
        }

        // Redraw into a context with a known, fixed pixel format (RGBA8, premultiplied-last) so the
        // byte layout read below doesn't depend on whatever format ImageRenderer happened to produce.
        let pixelWidth = Int(uiImage.size.width * uiImage.scale)
        let pixelHeight = Int(uiImage.size.height * uiImage.scale)
        var pixels = [UInt8](repeating: 0, count: pixelWidth * pixelHeight * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let context = CGContext(
            data: &pixels,
            width: pixelWidth,
            height: pixelHeight,
            bitsPerComponent: 8,
            bytesPerRow: pixelWidth * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            Issue.record("failed to create measurement bitmap context")
            return (0, 0)
        }
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: pixelWidth, height: pixelHeight))

        let midRow = pixelHeight / 2
        var minX = pixelWidth
        var maxX = -1
        for x in 0..<pixelWidth {
            let offset = (midRow * pixelWidth + x) * 4
            let isWhite = pixels[offset] > 240 && pixels[offset + 1] > 240 && pixels[offset + 2] > 240
            if !isWhite {
                minX = min(minX, x)
                maxX = max(maxX, x)
            }
        }
        guard maxX >= minX else {
            Issue.record("no non-white badge content found in rendered row")
            return (0, 0)
        }
        return (CGFloat(minX), CGFloat(pixelWidth - 1 - maxX))
    }
}
