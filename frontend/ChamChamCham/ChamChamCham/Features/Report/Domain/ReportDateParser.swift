//
//  ReportDateParser.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

enum ReportDateParser {
    private static let timeZone = TimeZone(identifier: "Asia/Seoul") ?? TimeZone(secondsFromGMT: 0)!

    static func localDate(from raw: String?) -> Date? {
        guard let raw else { return nil }
        return formatter("yyyy-MM-dd").date(from: raw)
    }

    static func localDateTime(from raw: String?) -> Date? {
        guard let raw else { return nil }
        for format in ["yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss"] {
            if let date = formatter(format).date(from: raw) {
                return date
            }
        }
        return nil
    }

    static func requiredLocalDateTime(from raw: String) -> Date {
        localDateTime(from: raw) ?? .distantPast
    }

    static func displayDate(_ date: Date) -> String {
        formatter("yyyy.MM.dd").string(from: date)
    }

    private static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = format
        return formatter
    }
}
