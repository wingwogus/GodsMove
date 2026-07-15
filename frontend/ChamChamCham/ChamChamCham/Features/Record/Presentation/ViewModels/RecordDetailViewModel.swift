//
//  RecordDetailViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// Drives the read-only 영농기록 결과 상세 화면. Loads one record by id via `GET /farming-records/{id}` and
/// exposes loading / loaded / failed so the view can render the states Figma doesn't (Figma has only success).
/// 수정/삭제(⋮)는 디자인 미캡처 — 후속.
@MainActor
@Observable
final class RecordDetailViewModel {
    enum LoadState {
        case loading
        case loaded(RecordDetail)
        case failed(String)
    }

    private(set) var state: LoadState = .loading
    private(set) var isDeleting = false
    var deleteError: String?

    private let recordId: UUID
    private let repository: any RecordRepository

    init(recordId: UUID, repository: any RecordRepository) {
        self.recordId = recordId
        self.repository = repository
    }

    func onAppear() async {
        // Load once; retry re-enters through `load()`.
        if case .loaded = state { return }
        await load()
    }

    func load() async {
        state = .loading
        do {
            state = .loaded(try await repository.fetchDetail(id: recordId))
        } catch {
            state = .failed(RecordErrorMessage.text(for: error))
        }
    }

    /// Deletes the record. Returns `true` on success so the view can pop + refresh the list. On failure the
    /// detail stays visible and `deleteError` surfaces the reason.
    func delete() async -> Bool {
        isDeleting = true
        deleteError = nil
        defer { isDeleting = false }
        do {
            try await repository.deleteRecord(id: recordId)
            return true
        } catch {
            deleteError = RecordErrorMessage.text(for: error)
            return false
        }
    }
}
