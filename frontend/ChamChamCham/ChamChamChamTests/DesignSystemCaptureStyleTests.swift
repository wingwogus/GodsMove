//
//  DesignSystemCaptureStyleTests.swift
//  ChamChamCham
//
//  Created by iyungui on 7/11/26.
//

import SwiftUI
import Testing
import UIKit
@testable import ChamChamCham

@Suite("Captured design system styles")
struct DesignSystemCaptureStyleTests {

    @Test("filled property overrides the runtime value state")
    func filledState() {
        #expect(AppFieldContainer<EmptyView>.resolvedFilled(override: nil, hasValue: false) == false)
        #expect(AppFieldContainer<EmptyView>.resolvedFilled(override: nil, hasValue: true) == true)
        #expect(AppFieldContainer<EmptyView>.resolvedFilled(override: false, hasValue: true) == false)
        #expect(AppFieldContainer<EmptyView>.resolvedFilled(override: true, hasValue: false) == true)
    }

    @Test("chip follows the 7.8 and 7.9 icon and style states")
    @MainActor
    func chipStyles() {
        #expect(AppChip.trailingPadding(hasTrailingIcon: true) == 8)
        #expect(AppChip.trailingPadding(hasTrailingIcon: false) == 12)
        #expect(hex(AppChip.fillColor(style: .solid, isSelected: false)) == hex(Color.Object.muted))
        #expect(hex(AppChip.fillColor(style: .solidPastel, isSelected: false)) == hex(Color.Object.default))
        #expect(hex(AppChip.borderColor(style: .solidPastel, isSelected: false)) == hex(Color.Border.subtle))
    }

    @Test("community chips reuse the captured solid and solid-pastel variants")
    @MainActor
    func communityChipStyles() {
        #expect(hex(AppChip.fillColor(style: .solid, isSelected: true)) == 0x343434)
        #expect(hex(AppChip.fillColor(style: .solidPastel, isSelected: false)) == 0xFFFFFF)
        #expect(hex(AppChip.borderColor(style: .solid, isSelected: true)) == nil)
        #expect(hex(AppChip.borderColor(style: .solidPastel, isSelected: false)) == 0xF3F3F3)
    }

    @Test("community post row preserves the captured vertical geometry")
    func communityPostRowGeometry() {
        #expect(AppListItem<EmptyView>.Size.medium.canvasSize.height == 160)
        #expect(CommunityPostRow.Layout.interRowSpacing == 20)
    }

    @Test("toggle uses the captured dimensions and off-state color")
    @MainActor
    func toggleStyles() {
        #expect(AppToggle.trackSize == CGSize(width: 48, height: 28))
        #expect(AppToggle.thumbSize == 24)
        #expect(hex(AppToggle.trackColor(isOn: false, isEnabled: true)) == hex(Color.Object.strong))
        #expect(hex(AppToggle.trackColor(isOn: true, isEnabled: true)) == hex(Color.Object.primary))
    }

    @Test("image uploader sizes match the captured filled states")
    func imageUploaderSizes() {
        #expect(AppImageUploadSlot<EmptyView>.Size.medium.dimension == 96)
        #expect(AppImageUploadSlot<EmptyView>.Size.medium.cornerRadius == 8)
        #expect(AppImageUploadSlot<EmptyView>.Size.medium.removeIconSize == 24)
        #expect(AppImageUploadSlot<EmptyView>.Size.medium.removeInset == 12)
        #expect(AppImageUploadSlot<EmptyView>.Size.small.dimension == 64)
        #expect(AppImageUploadSlot<EmptyView>.Size.small.cornerRadius == 4)
        #expect(AppImageUploadSlot<EmptyView>.Size.small.removeIconSize == 20)
        #expect(AppImageUploadSlot<EmptyView>.Size.small.removeInset == 8)
    }

    @Test("card sizes map to the 7.8 layout dimensions")
    func cardSizes() {
        #expect(AppCard<EmptyView>.Size.xsmall.canvasSize == CGSize(width: 168, height: 168))
        #expect(AppCard<EmptyView>.Size.small.canvasSize == CGSize(width: 350, height: 180))
        #expect(AppCard<EmptyView>.Size.medium.canvasSize == CGSize(width: 258, height: 261))
        #expect(AppCard<EmptyView>.Size.large.canvasSize == CGSize(width: 350, height: 334))
        #expect(AppCard<EmptyView>.Size.medium.cornerRadius == 20)
        #expect(AppCard<EmptyView>.Size.large.cornerRadius == 24)
    }

    @Test("card selection matches the captured xsmall and small states")
    @MainActor
    func selectedCardStyles() {
        typealias Card = AppCard<EmptyView>

        #expect(Card.usesSelectedStyle(size: .xsmall, isSelected: true))
        #expect(Card.usesSelectedStyle(size: .small, isSelected: true))
        #expect(!Card.usesSelectedStyle(size: .medium, isSelected: true))
        #expect(!Card.usesSelectedStyle(size: .large, isSelected: true))
        #expect(!Card.usesSelectedStyle(size: .small, isSelected: false))

        #expect(hex(Card.backgroundColor(size: .small, isSelected: true)) == 0xE4F8E3)
        #expect(hex(Card.borderColor(size: .small, isSelected: true)) == 0x38C284)
        #expect(hex(Card.titleColor(size: .small, isSelected: true)) == 0x1A1A1A)
        #expect(hex(Card.captionColor(size: .small, isSelected: true)) == 0x4F4F4F)
        #expect(hex(Card.badgeBackgroundColor(size: .small, isSelected: true)) == 0xFFFFFF)
        #expect(hex(Card.badgeTextColor(size: .small, isSelected: true)) == 0x27865C)

        #expect(hex(Card.backgroundColor(size: .small, isSelected: false)) == 0xFFFFFF)
        #expect(hex(Card.borderColor(size: .small, isSelected: false)) == 0xE0E0E0)
        #expect(Card.smallCaptionLineLimit == 2)
    }

    @Test("list keeps the new large row and the policy xlarge row separate")
    func listSizes() {
        #expect(AppListItem<EmptyView>.Size.xsmall.canvasSize == CGSize(width: 390, height: 58))
        #expect(AppListItem<EmptyView>.Size.small.canvasSize == CGSize(width: 390, height: 120))
        #expect(AppListItem<EmptyView>.Size.medium.canvasSize == CGSize(width: 390, height: 160))
        #expect(AppListItem<EmptyView>.Size.large.canvasSize == CGSize(width: 390, height: 184))
        #expect(AppListItem<EmptyView>.Size.xlarge.canvasSize == CGSize(width: 390, height: 169))
        #expect(AppListItem<EmptyView>.Size.large.thumbnailSide == 120)
        #expect(AppListItem<EmptyView>.Size.xlarge.thumbnailSide == nil)
    }

    @Test("comment follows the captured profile, attachment, and read-more states")
    @MainActor
    func commentStyles() {
        #expect(AppComment<EmptyView>.avatarSize == 32)
        #expect(AppComment<EmptyView>.attachmentSize == 96)
        #expect(AppComment<EmptyView>.attachmentCornerRadius == 8)
        #expect(AppComment<EmptyView>.collapsedLineLimit == 3)
        #expect(AppComment<EmptyView>.readMoreTitle(isReadMoreActive: false) == "자세히 보기")
        #expect(AppComment<EmptyView>.readMoreTitle(isReadMoreActive: true) == "간략히 보기")
        #expect(hex(AppComment<EmptyView>.nicknameColor) == hex(Color.Text.subtle))
        #expect(hex(AppComment<EmptyView>.dateColor) == hex(Color.Text.muted))
    }

    @Test("comment input follows the captured focus, filled, and image states")
    @MainActor
    func commentInputStyles() {
        #expect(AppCommentInput.inputRowHeight == 48)
        #expect(AppCommentInput.attachmentSize == 64)
        #expect(AppCommentInput.attachmentCornerRadius == 4)
        #expect(AppCommentInput.containerHeight(isFocused: true, hasImage: false) == 68)
        #expect(AppCommentInput.containerHeight(isFocused: false, hasImage: false) == 88)
        #expect(AppCommentInput.containerHeight(isFocused: true, hasImage: true) == 132)
        #expect(AppCommentInput.containerHeight(isFocused: false, hasImage: true) == 152)
        #expect(AppCommentInput.resolvedFilled(override: nil, hasText: false) == false)
        #expect(AppCommentInput.resolvedFilled(override: true, hasText: false) == true)
        #expect(hex(AppCommentInput.submitBackground(isFilled: true)) == hex(Color.Object.bold))
        #expect(hex(AppCommentInput.submitBackground(isFilled: false)) == hex(Color.Object.strong))
    }

    @MainActor
    private func hex(_ color: Color?) -> UInt32? {
        guard let color else { return nil }

        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0

        guard UIColor(color).getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return nil
        }

        return UInt32((red * 255).rounded()) << 16
            | UInt32((green * 255).rounded()) << 8
            | UInt32((blue * 255).rounded())
    }
}
