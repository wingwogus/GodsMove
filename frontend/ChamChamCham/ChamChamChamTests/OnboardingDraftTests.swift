//
//  OnboardingDraftTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/11/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("OnboardingDraft")
struct OnboardingDraftTests {

    @Test("starts with one active farm draft")
    func startsWithOneActiveFarmDraft() {
        let draft = OnboardingDraft()

        #expect(draft.farms.count == 1)
        #expect(draft.activeFarmIndex == 0)
        #expect(draft.activeFarm.farmName == "")
        #expect(draft.managementType == .agriculturalIndividual)
    }

    @Test("compatibility farm accessors read and write the active farm")
    func compatibilityAccessorsUseActiveFarm() {
        var draft = OnboardingDraft()
        let firstCrop = UUID()
        let secondCrop = UUID()
        draft.cropIDs = [firstCrop]
        draft.farmName = "첫번째농장"

        draft.addEmptyFarmAndSelect()
        draft.cropIDs = [secondCrop]
        draft.farmName = "두번째농장"

        #expect(draft.farms.count == 2)
        #expect(draft.activeFarmIndex == 1)
        #expect(draft.activeFarm.cropIDs == [secondCrop])
        #expect(draft.activeFarm.farmName == "두번째농장")
        #expect(draft.farms[0].cropIDs == [firstCrop])
        #expect(draft.farms[0].farmName == "첫번째농장")
    }

    @Test("decodes old single-farm snapshots into the new farms array")
    func decodesLegacySingleFarmSnapshot() throws {
        let cropID = UUID()
        let legacyJSON = """
        {
          "name": "홍길동",
          "nickname": "길동",
          "phone": "010-1234-5678",
          "birthDate": 631152000,
          "experienceYears": 3,
          "managementType": "AGRICULTURAL_INDIVIDUAL",
          "profileImageFileName": null,
          "profileMediaId": null,
          "cropIDs": ["\(cropID.uuidString)"],
          "farmName": "레거시농장",
          "farmRoadAddress": "전북 전주시 완산구 레거시로 1",
          "farmJibunAddress": "전북 전주시 완산구 레거시동 1",
          "farmLatitude": 35.8465,
          "farmLongitude": 127.1292,
          "farmPNU": "4511111111",
          "farmLandCategory": "전",
          "farmAreaSqm": 1200.5,
          "farmAreaIsManualEntry": true
        }
        """.data(using: .utf8)!

        let draft = try JSONDecoder().decode(OnboardingDraft.self, from: legacyJSON)

        #expect(draft.farms.count == 1)
        #expect(draft.activeFarmIndex == 0)
        #expect(draft.activeFarm.cropIDs == [cropID])
        #expect(draft.activeFarm.farmName == "레거시농장")
        #expect(draft.activeFarm.farmRoadAddress == "전북 전주시 완산구 레거시로 1")
        #expect(draft.activeFarm.farmAreaSqm == 1200.5)
        #expect(draft.activeFarm.farmAreaIsManualEntry)
    }

    @Test("maxAllowedExperienceYears falls back to 100 when birthDate is absent")
    func maxAllowedExperienceYearsFallsBackTo100() {
        var draft = OnboardingDraft()
        draft.birthDate = nil

        #expect(draft.currentAge == nil)
        #expect(draft.maxAllowedExperienceYears == 100)
    }

    @Test("maxAllowedExperienceYears is capped at the age derived from birthDate")
    func maxAllowedExperienceYearsCappedAtAge() {
        var draft = OnboardingDraft()
        draft.birthDate = Calendar.current.date(byAdding: .year, value: -25, to: Date())

        #expect(draft.currentAge == 25)
        #expect(draft.maxAllowedExperienceYears == 25)
    }
}
