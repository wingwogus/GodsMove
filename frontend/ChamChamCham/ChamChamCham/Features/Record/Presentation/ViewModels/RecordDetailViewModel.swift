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

    /// "참참참의 코칭" card state, driven independently of the record so it never blocks the detail render.
    /// `preparing` covers both "still generating" and "not requested yet" — the view shows the same 준비 중 copy.
    enum CoachingState {
        case loading
        case preparing
        case ready(CoachingFeedback)
        case unavailable
    }

    private(set) var state: LoadState = .loading
    private(set) var coachingState: CoachingState = .loading
    private(set) var isDeleting = false
    var deleteError: String?

    private let recordId: UUID
    private let repository: any RecordRepository
    private let coachingPollInterval: Duration
    private let coachingMaxAttempts: Int
    private var coachingStarted = false

    /// `coachingPollInterval`/`coachingMaxAttempts` are injectable so tests can poll instantly. Defaults poll
    /// every 3s up to 8 times (~24s) before settling on the last observed state.
    init(
        recordId: UUID,
        repository: any RecordRepository,
        coachingPollInterval: Duration = .seconds(3),
        coachingMaxAttempts: Int = 8
    ) {
        self.recordId = recordId
        self.repository = repository
        self.coachingPollInterval = coachingPollInterval
        self.coachingMaxAttempts = coachingMaxAttempts
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

    /// Loads the record's AI coaching, polling while it's still being generated. Feedback is created
    /// asynchronously server-side, so an early GET commonly returns notFound/pending; we retry up to
    /// `coachingMaxAttempts` before giving up and leaving the card in its last state. Runs once per view
    /// appearance; safe to re-invoke (guarded). Respects cancellation (the driving `.task` cancels on disappear).
    func loadCoaching() async {
        guard !coachingStarted else { return }
        coachingStarted = true

        for attempt in 0..<coachingMaxAttempts {
            do {
                let coaching = try await repository.fetchCoaching(id: recordId)
                switch coaching.status {
                case .ready:
                    // READY without a body is a backend contract violation; treat as still-preparing.
                    if let feedback = coaching.feedback {
                        coachingState = .ready(feedback)
                    } else {
                        coachingState = .preparing
                    }
                    return
                case .failed:
                    coachingState = .unavailable
                    return
                case .pending, .stale, .notFound:
                    coachingState = .preparing
                }
            } catch is CancellationError {
                return
            } catch {
                // Network/server hiccup: stay quiet (준비 중) mid-poll; only surface failure once attempts run out.
                coachingState = (attempt == coachingMaxAttempts - 1) ? .unavailable : .preparing
            }

            guard attempt < coachingMaxAttempts - 1 else { return }
            do {
                try await Task.sleep(for: coachingPollInterval)
            } catch {
                return // cancelled while waiting
            }
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
