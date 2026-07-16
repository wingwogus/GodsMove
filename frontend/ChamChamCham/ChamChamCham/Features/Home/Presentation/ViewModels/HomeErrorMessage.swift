//
//  HomeErrorMessage.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// Maps thrown errors to the short Korean copy the home sections show. Mirrors `RecordErrorMessage`/
/// `CommunityErrorMessage`.
enum HomeErrorMessage {
    static func text(for error: Error) -> String {
        guard let apiError = error as? APIError else {
            return "문제가 발생했어요. 잠시 후 다시 시도해주세요."
        }
        switch apiError {
        case .unauthorized:
            return "로그인이 필요해요."
        // FARM_001은 원시 i18n 키(`error.farm_not_found`)라 Hangul 체크를 안 타므로 코드로 직접 잡는다 —
        // 날씨/최근기록 섹션이 예전엔 등록 농지 없음을 클라이언트에서 먼저 검사해 이 문구를 보여줬는데,
        // weather/home이 이제 그 검사를 서버에서 대신 하므로 여기서 매핑을 이어받는다.
        case .apiError(let code, _) where code == "FARM_001":
            return "등록된 농장이 없어요"
        case let .apiError(_, message) where message.looksLikeUserFacingErrorMessage:
            return message
        case .network:
            return "네트워크 연결을 확인해주세요."
        default:
            return "문제가 발생했어요. 잠시 후 다시 시도해주세요."
        }
    }
}
