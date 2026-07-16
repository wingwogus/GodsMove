//
//  DesignColorTokenTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/11/26.
//

import SwiftUI
import Testing
import UIKit
@testable import ChamChamCham

@Suite("Design color tokens")
struct DesignColorTokenTests {

    @Test("object/strong matches gray 200")
    func objectStrong() {
        #expect(hex(Color.Object.strong) == 0xE0E0E0)
    }

    @Test("icon/red matches red 500")
    func iconRed() {
        #expect(hex(Color.Icon.red) == 0xEF4444)
    }

    @Test("report chart palette matches the six approved Figma colors")
    func reportChartPalette() {
        #expect(Color.Chart.palette.compactMap(hex) == [
            0x38C284,
            0xA5E9B1,
            0xF7DC11,
            0xC8F468,
            0x81DAD8,
            0xB1CBDF,
        ])
    }

    private func hex(_ color: Color) -> UInt32? {
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
