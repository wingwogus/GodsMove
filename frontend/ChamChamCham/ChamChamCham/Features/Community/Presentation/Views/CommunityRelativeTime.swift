//
//  CommunityRelativeTime.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Formats a post/comment timestamp as the "N분 전" relative string the wireframes use. Anything parsed as
/// `.distantPast` (an unparseable server timestamp) renders empty rather than a nonsensical "56년 전".
enum CommunityRelativeTime {
    static func string(from date: Date, now: Date = Date()) -> String {
        guard date != .distantPast else { return "" }
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: now)
    }
}
