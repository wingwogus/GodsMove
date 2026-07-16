//
//  RecordCoachingDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

// Wire-shape mirror of `GET /api/v1/farming-records/{id}/feedback` → `RecordFeedbackResponses.StatusResponse`
// (backend commit `b943ba9e`, deployed — the endpoint returns 401 unauthenticated, i.e. the route is live).
//
// IMPORTANT — Swagger discrepancy: the deployed `docs/swagger/` schema documents this endpoint's `feedback`
// with the *report* shape (`summary`/`comparisons`/`strengths`/`improvements`) because Springdoc collapses the
// same-named `StatusResponse`/`FeedbackResponse` inner classes from the record AND report controllers into one
// schema, and the report variant won. The record controller's runtime JSON follows its own source DTO
// (`goodPoint` + `nextActions[{text,due,category}]`), which is what this mirrors. Backend source is authoritative
// here; verify end-to-end against the live endpoint (needs login).
//
// Following `RecordDetailDTOs`, enums (`status`, `due`) are decoded as plain `String` and resolved with
// tolerant mappings so a contract drift degrades gracefully instead of failing the decode. Fields the UI
// doesn't use yet (`feedbackId`, `sourceRevision`, `inputPrepared`, `failureCode`, `category`, timestamps)
// are omitted — Decodable ignores unknown JSON keys.

struct RecordFeedbackStatusResponseDTO: Decodable, Sendable {
    let status: String
    let feedback: FeedbackDTO?

    struct FeedbackDTO: Decodable, Sendable {
        let goodPoint: GoodPointDTO
        let nextActions: [NextActionDTO]
    }

    struct GoodPointDTO: Decodable, Sendable {
        let text: String
    }

    struct NextActionDTO: Decodable, Sendable {
        let text: String
        let due: String
    }
}

// MARK: - Domain mapping

extension RecordFeedbackStatusResponseDTO {
    func toDomain() -> RecordCoaching {
        let status: RecordCoaching.Status
        switch self.status {
        case "READY": status = .ready
        case "PENDING": status = .pending
        case "FAILED": status = .failed
        case "STALE": status = .stale
        // Unknown status → treat as still-preparing so the view keeps polling rather than showing failure.
        default: status = .stale
        }

        // The backend only fills `feedback` when READY; guard so a stray body on a non-ready status is ignored.
        let feedback: CoachingFeedback? = {
            guard status == .ready, let feedback = self.feedback else { return nil }
            return CoachingFeedback(
                goodPoint: feedback.goodPoint.text,
                nextActions: feedback.nextActions.map {
                    CoachingNextAction(text: $0.text, due: CoachingActionDue(raw: $0.due))
                }
            )
        }()

        return RecordCoaching(status: status, feedback: feedback)
    }
}
