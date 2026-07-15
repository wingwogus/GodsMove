//
//  AppDropdownStyleTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/11/26.
//

import Testing
@testable import ChamChamCham

@Suite("AppDropdown styles")
struct AppDropdownStyleTests {

    @Test("selection and error map to Figma variants")
    @MainActor
    func variants() {
        #expect(AppDropdown<String>.variant(isFilled: false, isError: false) == .default)
        #expect(AppDropdown<String>.variant(isFilled: true, isError: false) == .filled)
        #expect(AppDropdown<String>.variant(isFilled: false, isError: true) == .error)
        #expect(AppDropdown<String>.variant(isFilled: true, isError: true) == .error)
    }
}
