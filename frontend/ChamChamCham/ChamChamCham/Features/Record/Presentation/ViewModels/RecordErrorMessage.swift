//
//  RecordErrorMessage.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

/// Maps thrown errors to the short Korean copy the record screens show. Business rejections from the backend
/// (`APIError.apiError`) carry a user-facing `message`, so we surface that; everything else gets a generic line.
enum RecordErrorMessage {
    static func text(for error: Error) -> String {
        guard let apiError = error as? APIError else {
            return "문제가 발생했어요. 잠시 후 다시 시도해주세요."
        }
        switch apiError {
        case .unauthorized:
            return "로그인이 필요해요."
        case let .apiError(_, message) where message.looksLikeUserFacingErrorMessage:
            return message
        case .network:
            return "네트워크 연결을 확인해주세요."
        default:
            return "문제가 발생했어요. 잠시 후 다시 시도해주세요."
        }
    }
}
