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
}
