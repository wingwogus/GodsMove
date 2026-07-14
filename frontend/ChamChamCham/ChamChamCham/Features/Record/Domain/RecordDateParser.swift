//
//  RecordDateParser.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

/// Parses the backend's `LocalDateTime` timestamps (`workedAt`, `createdAt`) into `Date`.
///
/// Same wire shape as the community feed (ISO-8601 without a zone offset, optional fractional seconds), so the
/// logic mirrors `CommunityDateParser`. Kept feature-local to avoid coupling Record to Community; if a third
/// feature needs it, promote a single parser into `Core/`.
enum RecordDateParser {
    private static let formats = ["yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss"]

    static func date(from raw: String) -> Date {
        for format in formats {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = .current
            formatter.dateFormat = format
            if let date = formatter.date(from: raw) {
                return date
            }
        }
        return .distantPast
    }
}
