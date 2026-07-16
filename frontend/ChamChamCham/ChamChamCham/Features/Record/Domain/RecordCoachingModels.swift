//
//  RecordCoachingModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// "참참참의 코칭" — one farming record's AI coaching feedback. Projection of the backend
/// `RecordFeedbackResponses.StatusResponse` (`GET /api/v1/farming-records/{id}/feedback`, backend commit
/// `b943ba9e`). Feedback is generated asynchronously, so a record may have no coaching yet: the GET returns
/// `COACHING_001`/404 when no row exists, which the repository folds into `.notFound` (a "준비 중" state the
/// view polls on) rather than an error.
struct RecordCoaching: Sendable, Equatable {
    /// Lifecycle of the coaching feedback. `notFound` is client-only (the 404-for-no-row case); the rest mirror
    /// the backend `RecordFeedbackStatus`. `pending`/`stale`/`notFound` all mean "keep polling"; only `ready`
    /// carries feedback content, and `failed` is terminal.
    enum Status: Sendable {
        case pending
        case ready
        case failed
        case stale
        case notFound
    }

    let status: Status
    /// Non-nil only when `status == .ready` (the backend omits the body until then).
    let feedback: CoachingFeedback?
}

/// The coaching body shown when feedback is `READY`: one 잘한 점 line + 2~3 다음 할 일 items.
struct CoachingFeedback: Sendable, Equatable {
    let goodPoint: String
    let nextActions: [CoachingNextAction]
}

/// One 다음 할 일 item — an action with a suggested timeframe. (Backend also sends a `category`; not displayed
/// yet, so not modeled — YAGNI.)
struct CoachingNextAction: Sendable, Equatable {
    let text: String
    let due: CoachingActionDue
}

/// When a next action should be done. Tolerant of unknown backend values (contract may drift before Swagger
/// catches up) — unmapped strings become `.unknown`, which the view renders with no timeframe chip.
enum CoachingActionDue: Sendable {
    case today
    case thisWeek
    case nextWeek
    case nextCheck
    case unknown

    init(raw: String) {
        switch raw {
        case "TODAY": self = .today
        case "THIS_WEEK": self = .thisWeek
        case "NEXT_WEEK": self = .nextWeek
        case "NEXT_CHECK": self = .nextCheck
        default: self = .unknown
        }
    }

    /// Chip copy; `nil` for `.unknown` so the view omits the chip entirely.
    var label: String? {
        switch self {
        case .today: "오늘"
        case .thisWeek: "이번 주"
        case .nextWeek: "다음 주"
        case .nextCheck: "다음 점검 때"
        case .unknown: nil
        }
    }
}
