//
//  FarmAddViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("FarmAddViewModel")
struct FarmAddViewModelTests {

    private func makeViewModel() -> (FarmAddViewModel, StubFarmRepository) {
        let repository = StubFarmRepository()
        let viewModel = FarmAddViewModel(
            farmRepository: repository,
            cropCatalog: PreviewCropCatalogService()
        )
        return (viewModel, repository)
    }

    @Test("cannot save without name, address, and resolved coordinate")
    func requiresNameAddressCoordinate() {
        let (viewModel, _) = makeViewModel()
        #expect(!viewModel.canSave)

        viewModel.farmName = "행복농장"
        #expect(!viewModel.canSave) // still missing address/coordinate

        viewModel.location.selectedAddress = JusoAddress(
            roadAddrPart1: "전북 전주시 완산구 테스트로 1",
            jibunAddr: "전북 전주시 완산구 테스트동 1",
            bdNm: ""
        )
        #expect(!viewModel.canSave) // still missing coordinate

        viewModel.location.resolvedCoordinate = GeoPoint(latitude: 35.8, longitude: 127.1)
        #expect(viewModel.canSave)
    }

    @Test("save builds a SaveFarmRequest with address, coordinate, and selected crops")
    func saveBuildsRequest() async {
        let (viewModel, repository) = makeViewModel()
        let cropA = Crop(id: UUID(), name: "인삼", categoryCode: "R", categoryLabel: "뿌리")
        let cropB = Crop(id: UUID(), name: "고추", categoryCode: "F", categoryLabel: "과채")

        viewModel.farmName = "행복농장"
        viewModel.location.selectedAddress = JusoAddress(
            roadAddrPart1: "전북 전주시 완산구 테스트로 1",
            jibunAddr: "전북 전주시 완산구 테스트동 1",
            bdNm: ""
        )
        viewModel.location.resolvedCoordinate = GeoPoint(latitude: 35.8, longitude: 127.1)
        viewModel.selectedCrops = [cropA, cropB]

        let saved = await viewModel.save()
        #expect(saved)

        let creates = await repository.creates()
        #expect(creates.count == 1)
        let request = creates.first
        #expect(request?.name == "행복농장")
        #expect(request?.roadAddress == "전북 전주시 완산구 테스트로 1")
        #expect(request?.latitude == 35.8)
        #expect(request?.longitude == 127.1)
        #expect(request?.cropIds == [cropA.id, cropB.id])
        #expect(request?.areaIsManualEntry == true) // no parcel resolved
    }
}
