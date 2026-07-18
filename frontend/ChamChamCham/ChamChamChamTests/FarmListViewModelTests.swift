//
//  FarmListViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("FarmListViewModel")
struct FarmListViewModelTests {

    @Test("loads farms from the repository")
    func loadsFarms() async {
        let repository = StubFarmRepository(farms: [
            MyPageFixtures.standaloneFarm(name: "A"),
            MyPageFixtures.standaloneFarm(name: "B")
        ])
        let viewModel = FarmListViewModel(repository: repository)

        await viewModel.load()

        #expect(viewModel.count == 2)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("exiting delete mode clears the selection")
    func exitingDeleteModeClearsSelection() {
        let viewModel = FarmListViewModel(repository: StubFarmRepository())
        let id = UUID()

        viewModel.toggleDeleteMode()
        viewModel.toggleSelection(id)
        #expect(viewModel.isSelected(id))

        viewModel.toggleDeleteMode() // exit
        #expect(!viewModel.isDeleting)
        #expect(!viewModel.isSelected(id))
    }

    @Test("toggleSelection adds then removes an id")
    func toggleSelectionAddsThenRemoves() {
        let viewModel = FarmListViewModel(repository: StubFarmRepository())
        let id = UUID()

        viewModel.toggleSelection(id)
        #expect(viewModel.isSelected(id))
        viewModel.toggleSelection(id)
        #expect(!viewModel.isSelected(id))
    }

    @Test("deleteSelected deletes each selected farm and leaves delete mode")
    func deleteSelectedRemovesAndExits() async {
        let keepId = UUID()
        let dropId = UUID()
        let repository = StubFarmRepository(farms: [
            MyPageFixtures.standaloneFarm(id: keepId, name: "A"),
            MyPageFixtures.standaloneFarm(id: dropId, name: "B")
        ])
        let viewModel = FarmListViewModel(repository: repository)
        await viewModel.load()

        viewModel.toggleDeleteMode()
        viewModel.toggleSelection(dropId)
        await viewModel.deleteSelected()

        #expect(await repository.deletes() == [dropId])
        #expect(!viewModel.isDeleting)
        #expect(viewModel.selectedForDeletion.isEmpty)
    }

    @Test("deleteSelected sets the fixed delete toast on success")
    func deleteSelectedSetsToast() async {
        let dropId = UUID()
        let repository = StubFarmRepository(farms: [MyPageFixtures.standaloneFarm(id: dropId, name: "A")])
        let viewModel = FarmListViewModel(repository: repository)
        await viewModel.load()

        viewModel.toggleDeleteMode()
        viewModel.toggleSelection(dropId)
        await viewModel.deleteSelected()

        #expect(viewModel.toastMessage == "농지 삭제 완료되었습니다.")
    }

    @Test("renameFarm PUTs a rebuilt request with only the name changed and shows the fixed toast")
    func renameFarmUpdatesNameOnly() async {
        let farmId = UUID()
        let farm = MyPageFixtures.standaloneFarm(id: farmId, name: "행복농장")
        let repository = StubFarmRepository(farms: [farm])
        let viewModel = FarmListViewModel(repository: repository)
        await viewModel.load()

        await viewModel.renameFarm(farm, to: "새이름농장")

        let updates = await repository.updates()
        #expect(updates.count == 1)
        #expect(updates.first?.id == farmId)
        #expect(updates.first?.request.name == "새이름농장")
        #expect(updates.first?.request.roadAddress == farm.roadAddress)
        #expect(updates.first?.request.latitude == farm.latitude)
        #expect(updates.first?.request.longitude == farm.longitude)
        #expect(viewModel.toastMessage == "농지 정보 수정 완료되었습니다.")
    }

    @Test("renameFarm is a no-op for an empty or unchanged name")
    func renameFarmNoOpWhenUnchanged() async {
        let farm = MyPageFixtures.standaloneFarm(name: "행복농장")
        let repository = StubFarmRepository(farms: [farm])
        let viewModel = FarmListViewModel(repository: repository)

        await viewModel.renameFarm(farm, to: "행복농장")
        await viewModel.renameFarm(farm, to: "   ")

        #expect(await repository.updates().isEmpty)
        #expect(viewModel.toastMessage == nil)
    }

    @Test("renameFarm surfaces an error and skips the toast when the farm has no coordinates")
    func renameFarmFailsWithoutCoordinates() async {
        let farmId = UUID()
        let farm = StandaloneFarmResponseDTO(
            farmId: farmId,
            name: "레거시농장",
            roadAddress: "전북 전주시 완산구 테스트로 1",
            jibunAddress: nil,
            latitude: nil,
            longitude: nil,
            pnu: nil,
            landCategory: nil,
            areaSqm: nil,
            areaIsManualEntry: true,
            boundaryCoordinates: [],
            dataSource: .onboardingJusoVWorld,
            crops: []
        )
        let repository = StubFarmRepository(farms: [farm])
        let viewModel = FarmListViewModel(repository: repository)

        await viewModel.renameFarm(farm, to: "새이름")

        #expect(await repository.updates().isEmpty)
        #expect(viewModel.toastMessage == nil)
        #expect(viewModel.errorMessage != nil)
    }

    @Test("updateCrops PUTs a rebuilt request with only cropIds changed and shows the fixed toast")
    func updateCropsUpdatesCropIdsOnly() async {
        let farmId = UUID()
        let farm = MyPageFixtures.standaloneFarm(id: farmId, name: "행복농장")
        let repository = StubFarmRepository(farms: [farm])
        let viewModel = FarmListViewModel(repository: repository)
        await viewModel.load()

        let cropId = UUID()
        await viewModel.updateCrops(farm, cropIds: [cropId])

        let updates = await repository.updates()
        #expect(updates.count == 1)
        #expect(updates.first?.request.cropIds == [cropId])
        #expect(updates.first?.request.name == farm.name)
        #expect(viewModel.toastMessage == "농지 정보 수정 완료되었습니다.")
    }

    @Test("updateLocation replaces every location-derived field and reports success")
    func updateLocationReplacesLocationFields() async {
        let farmId = UUID()
        let farm = MyPageFixtures.standaloneFarm(id: farmId, name: "행복농장")
        let repository = StubFarmRepository(farms: [farm])
        let viewModel = FarmListViewModel(repository: repository)
        await viewModel.load()

        let location = FarmLocationViewModel()
        location.selectedAddress = JusoAddress(
            roadAddrPart1: "전북 전주시 완산구 새주소로 9",
            jibunAddr: "전북 전주시 완산구 새주소동 9",
            bdNm: ""
        )
        location.resolvedCoordinate = GeoPoint(latitude: 36.1, longitude: 127.4)

        let succeeded = await viewModel.updateLocation(farm, location: location)

        #expect(succeeded)
        let updates = await repository.updates()
        #expect(updates.first?.request.roadAddress == "전북 전주시 완산구 새주소로 9")
        #expect(updates.first?.request.latitude == 36.1)
        #expect(updates.first?.request.longitude == 127.4)
        #expect(updates.first?.request.name == farm.name)
        #expect(viewModel.toastMessage == "농지 정보 수정 완료되었습니다.")
    }
}
