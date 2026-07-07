//
//  CommunityDateParserTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("CommunityDateParser")
struct CommunityDateParserTests {

    private func components(_ date: Date) -> DateComponents {
        Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute, .second], from: date
        )
    }

    @Test("parses a LocalDateTime without fractional seconds")
    func withoutFractional() {
        let date = CommunityDateParser.date(from: "2026-07-07T09:30:15")
        #expect(date != .distantPast)

        let parts = components(date)
        #expect(parts.year == 2026)
        #expect(parts.month == 7)
        #expect(parts.day == 7)
        #expect(parts.hour == 9)
        #expect(parts.minute == 30)
        #expect(parts.second == 15)
    }

    @Test("parses a LocalDateTime with fractional seconds")
    func withFractional() {
        let date = CommunityDateParser.date(from: "2026-07-07T09:30:15.250")
        #expect(date != .distantPast)

        let parts = components(date)
        #expect(parts.hour == 9)
        #expect(parts.minute == 30)
        #expect(parts.second == 15)
    }

    @Test("falls back to distantPast on an unparseable value")
    func fallback() {
        #expect(CommunityDateParser.date(from: "not-a-date") == .distantPast)
        #expect(CommunityDateParser.date(from: "") == .distantPast)
    }
}
