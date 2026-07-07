//
//  OnboardingCompleteRequestDTOTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("OnboardingCompleteRequestDTO")
struct OnboardingCompleteRequestDTOTests {

    private func encodedJSON(_ dto: OnboardingCompleteRequestDTO) throws -> [String: Any] {
        let data = try JSONEncoder().encode(dto)
        return try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    @Test("maps draft fields to the confirmed wire keys")
    func mapsFieldsToWireKeys() throws {
        var draft = OnboardingTestFactory.validDraft()
        draft.experienceYears = 7

        let dto = try OnboardingCompleteRequestDTO(draft: draft)
        let json = try encodedJSON(dto)

        #expect(json["name"] as? String == "홍길동")
        #expect(json["phone"] as? String == "010-1234-5678")
        #expect(json["nickname"] as? String == "길동")
        #expect(json["birthDate"] as? String == "1990-01-01")
        #expect(json["experienceLevel"] as? Int == 7)
        #expect(json["managementType"] as? String == "AGRICULTURAL_INDIVIDUAL")

        let cropIds = try #require(json["cropIds"] as? [String])
        #expect(cropIds.count == 1)

        let farm = try #require(json["farm"] as? [String: Any])
        #expect(farm["roadAddress"] as? String == "서울시 강남구 테헤란로 1")
        #expect(farm["latitude"] as? Double == 35.8465)
        #expect(farm["longitude"] as? Double == 127.1292)
    }

    @Test("includes profileMediaId when the draft has one")
    func includesProfileMediaId() throws {
        var draft = OnboardingTestFactory.validDraft()
        let mediaId = UUID()
        draft.profileMediaId = mediaId

        let json = try encodedJSON(try OnboardingCompleteRequestDTO(draft: draft))

        #expect((json["profileMediaId"] as? String).flatMap(UUID.init) == mediaId)
    }

    @Test("omits profileMediaId when the draft has none")
    func omitsProfileMediaIdWhenAbsent() throws {
        let draft = OnboardingTestFactory.validDraft() // profileMediaId defaults to nil

        let json = try encodedJSON(try OnboardingCompleteRequestDTO(draft: draft))

        // Swift's synthesized Encodable omits nil optionals (encodeIfPresent). The backend field defaults to
        // null, so an absent key is correct — and, crucially, it's never a stale UUID from a prior pick.
        #expect(json["profileMediaId"] == nil)
    }

    @Test("throws when a required field is blank")
    func throwsOnMissingRequiredField() {
        var draft = OnboardingTestFactory.validDraft()
        draft.name = "   "

        #expect(throws: OnboardingSubmissionError.self) {
            _ = try OnboardingCompleteRequestDTO(draft: draft)
        }
    }

    @Test("throws when no crops are selected")
    func throwsOnEmptyCrops() {
        var draft = OnboardingTestFactory.validDraft()
        draft.cropIDs = []

        #expect(throws: OnboardingSubmissionError.self) {
            _ = try OnboardingCompleteRequestDTO(draft: draft)
        }
    }
}
