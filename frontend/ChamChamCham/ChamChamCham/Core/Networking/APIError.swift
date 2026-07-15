//
//  APIError.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

enum APIError: Error {
    case network(Error)
    case unauthorized
    case validation(message: String)
    case server(statusCode: Int)
    case decoding(Error)
    case apiError(code: String, message: String)
}

extension String {
    /// This app only ever sends real Korean copy as a business-error `message`; some backend error paths
    /// (e.g. an unmapped provider failure) leak a raw i18n key like `"error.weather_provider_unavailable"`
    /// instead. Presence of a Hangul character reliably tells the two apart, so `*ErrorMessage.text(for:)`
    /// implementations gate on this before showing `.apiError`'s `message` verbatim.
    var looksLikeUserFacingErrorMessage: Bool {
        contains { ("가"..."힣").contains($0) }
    }
}
