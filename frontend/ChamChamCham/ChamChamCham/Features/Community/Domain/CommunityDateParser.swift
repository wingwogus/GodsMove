//
//  CommunityDateParser.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Parses the backend's `LocalDateTime` timestamps (e.g. `createdAt`) into `Date`.
///
/// The values arrive as ISO-8601 *without* a zone offset and with an inconsistent fractional-seconds part
/// (`2026-07-07T12:34:56` or `2026-07-07T12:34:56.789`). `ISO8601DateFormatter` requires a zone, so it can't
/// parse these directly; we use fixed-format `DateFormatter`s and try the fractional variant first.
///
/// A `LocalDateTime` carries no zone, so we interpret it in the phone's current time zone — good enough for
/// the "N분 전" relative display the community list needs. If both formats fail (unexpected server shape),
/// we fall back to `Date.distantPast` rather than crashing, so one malformed row can't take down a whole page.
///
/// Formatters are built per call (not cached in shared statics) to stay clear of Swift 6's shared-mutable-state
/// rules without reaching for `nonisolated(unsafe)`. Community pages are small (~20 rows), so the cost is
/// negligible and this is not a hot loop.
enum CommunityDateParser {
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
