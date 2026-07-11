//
//  AppDividerStyleTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/11/26.
//

import SwiftUI
import Testing
import UIKit
@testable import ChamChamCham

@Suite("AppDivider styles")
struct AppDividerStyleTests {

    @Test("sizes match the Figma divider heights")
    func sizes() {
        #expect(AppDivider.Size.small.height == 2)
        #expect(AppDivider.Size.medium.height == 4)
    }

    @Test("divider uses object muted")
    func color() {
        #expect(hex(AppDivider.color) == hex(Color.Object.muted))
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
