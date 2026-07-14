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

        #expect(farm["cropIds"] == nil)
        #expect(farm["farmId"] == nil)

        let dataSource = try #require(farm["dataSource"] as? [String: String])
        #expect(dataSource["address"] == "JUSO")
        #expect(dataSource["coordinate"] == "V_WORLD_ADDRESS")
        #expect(dataSource["parcel"] == "V_WORLD_CADASTRAL")
        #expect(dataSource["landCharacteristic"] == "V_WORLD_LAND_CHARACTERISTIC")
    }

    @Test("maps the first farm to the current onboarding-complete wire contract")
    func mapsFirstFarmToSingleFarmContract() throws {
        let firstCrop = UUID()
        let secondCrop = UUID()
        var draft = OnboardingTestFactory.validDraft()
        draft.farms = [
            OnboardingFarmDraft(
                cropIDs: [firstCrop],
                farmName: "첫번째농장",
                farmRoadAddress: "전북 전주시 완산구 첫길 1",
                farmJibunAddress: "전북 전주시 완산구 첫동 1",
                farmLatitude: 35.1,
                farmLongitude: 127.1
            ),
            OnboardingFarmDraft(
                cropIDs: [secondCrop],
                farmName: "두번째농장",
                farmRoadAddress: "전북 전주시 완산구 둘길 2",
                farmJibunAddress: "전북 전주시 완산구 둘동 2",
                farmLatitude: 35.2,
                farmLongitude: 127.2
            )
        ]
        draft.activeFarmIndex = 1

        let json = try encodedJSON(try OnboardingCompleteRequestDTO(draft: draft))

        let cropIds = try #require(json["cropIds"] as? [String])
        #expect(cropIds.compactMap(UUID.init(uuidString:)) == [firstCrop])

        let farm = try #require(json["farm"] as? [String: Any])
        #expect(farm["name"] as? String == "첫번째농장")
        #expect(farm["roadAddress"] as? String == "전북 전주시 완산구 첫길 1")
        #expect(farm["latitude"] as? Double == 35.1)
        #expect(farm["longitude"] as? Double == 127.1)
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

    @Test("throws when nickname is blank")
    func throwsOnBlankNickname() {
        var draft = OnboardingTestFactory.validDraft()
        draft.nickname = "   "

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

    @Test("throws when the representative farm has more than five crops")
    func throwsOnTooManyCrops() {
        var draft = OnboardingTestFactory.validDraft()
        draft.cropIDs = (0..<6).map { _ in UUID() }

        #expect(throws: OnboardingSubmissionError.self) {
            _ = try OnboardingCompleteRequestDTO(draft: draft)
        }
    }

    @Test("throws when area is present but not positive")
    func throwsOnNonPositiveArea() {
        var draft = OnboardingTestFactory.validDraft()
        draft.farmAreaSqm = 0
        draft.farmAreaIsManualEntry = true

        #expect(throws: OnboardingSubmissionError.self) {
            _ = try OnboardingCompleteRequestDTO(draft: draft)
        }
    }
}
