//
//  CommunityComposeFigmaContractTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import CoreGraphics
import Testing
@testable import ChamChamCham

@Suite("Community compose Figma contract")
struct CommunityComposeFigmaContractTests {
    @Test("compose uses the captured text-area and attachment geometry")
    func geometry() {
        #expect(CommunityComposeView.Layout.horizontalInset == 20)
        #expect(CommunityComposeView.Layout.textAreaContentInset == 20)
        #expect(CommunityComposeView.Layout.titleHeight == 38)
        #expect(CommunityComposeView.Layout.minimumBodyLines == 9)
        #expect(CommunityComposeView.Layout.maximumBodyLines == 21)
        #expect(CommunityComposeView.Layout.descriptionSpacing == 12)
        #expect(CommunityComposeView.Layout.imageSpacing == 12)
        #expect(CommunityComposeView.Layout.sectionTopInset == 16)
        #expect(CommunityComposeView.Layout.majorSectionGap == 24)
    }
}
