//
//  FarmLocationViewModelValidationTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Testing
@testable import ChamChamCham

@MainActor
@Suite("FarmLocationViewModel validation")
struct FarmLocationViewModelValidationTests {

    @Test("manual fallback area must be positive to proceed")
    func manualAreaMustBePositive() {
        let viewModel = FarmLocationViewModel()
        viewModel.selectedAddress = JusoAddress(
            roadAddrPart1: "전북 전주시 완산구 테스트로 1",
            jibunAddr: "전북 전주시 완산구 테스트동 1",
            bdNm: ""
        )
        viewModel.manualAreaText = "0"

        #expect(!viewModel.canProceed)

        viewModel.manualAreaText = "12.5"

        #expect(viewModel.canProceed)
    }
}
