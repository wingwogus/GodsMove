//
//  RecordDetailLabelsTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/14/26.
//

import Foundation
import Testing
@testable import ChamChamCham

/// Covers the tolerant enum→label mapping and 작업 정보 row assembly for the record detail screen. These are
/// pure functions over the decoded `RecordDetailResponse`, and the deployed contract differs from the compose
/// (dev) contract — so the mapping must handle BOTH value sets and hide un-entered fields. See
/// `docs/figma/record/2026-07-14-record-detail-implementation-plan.md`.
@Suite("Record detail label mapping")
struct RecordDetailLabelsTests {
    /// Decodes a `{...}` detail body and returns "label=value" strings for concise assertions.
    private func pairs(_ json: String) throws -> [String] {
        let base = """
        {"id":"11111111-1111-1111-1111-111111111111","farmName":"1번밭","cropName":"도라지",
         "workType":"ETC","workedAt":"2026-12-12T09:00:00","weatherCondition":"맑음","weatherTemperature":14,
        """
        let full = base + json.dropFirst() // splice detail object(s) into the base object
        let dto = try JSONDecoder().decode(RecordDetailResponseDTO.self, from: Data(full.utf8))
        return RecordDetailLabels.infoRows(from: dto).map { "\($0.label)=\($0.value)" }
    }

    @Test("심기 - 씨앗")
    func plantingSeed() throws {
        let rows = try pairs(#"{"planting":{"propagationMethod":"SEED","seedAmount":50,"seedAmountUnit":"G"}}"#)
        #expect(rows == ["심기 방법=씨앗 심기", "심은 씨앗량=50g"])
    }

    @Test("심기 - 모종 (배포 shape: propagationMethod=CUTTING)")
    func plantingSeedlingDeployed() throws {
        let rows = try pairs(#"{"planting":{"propagationMethod":"CUTTING","seedlingCount":50,"seedlingUnit":"JU"}}"#)
        #expect(rows == ["심기 방법=모종 심기", "모종 번식법=꺾꽂이", "심은 모종 수=50주"])
    }

    @Test("심기 - 모종 (dev shape: plantingMethod=SEEDLING, PURCHASED 관용)")
    func plantingSeedlingDevPurchased() throws {
        let rows = try pairs(
            #"{"planting":{"plantingMethod":"SEEDLING","propagationMethod":"PURCHASED","seedlingCount":10,"seedlingUnit":"JU"}}"#
        )
        #expect(rows == ["심기 방법=모종 심기", "모종 번식법=시판 구매", "심은 모종 수=10주"])
    }

    @Test("물주기 (배포 전용 SPRINKLER → 살수 관용)")
    func wateringTolerant() throws {
        let rows = try pairs(#"{"watering":{"irrigationAmount":"NORMAL","irrigationMethod":"SPRINKLER"}}"#)
        #expect(rows == ["물의 양=보통", "진행 방식=살수"])
    }

    @Test("비료 주기 (배포 단위 KG)")
    func fertilizing() throws {
        let rows = try pairs(
            #"{"fertilizing":{"materialName":"신의한수비료","amount":50,"amountUnit":"KG","applicationMethod":"SOIL"}}"#
        )
        #expect(rows == ["사용 비료=신의한수비료", "비료 사용량=50kg", "진행 방식=토양에 주기"])
    }

    @Test("병해충 관리")
    func pestControl() throws {
        let rows = try pairs(#"""
        {"pestControl":{"pesticideName":"신의한수농약","pestTarget":"짱큰나방","pesticideAmount":20,
        "pesticideAmountUnit":"ML","totalSprayAmount":150,"totalSprayAmountUnit":"L"}}
        """#)
        #expect(rows == ["사용 농약=신의한수농약", "농약 사용량=20ml", "총 살포량=150L", "대상 병해충=짱큰나방"])
    }

    @Test("잡초 관리")
    func weeding() throws {
        let rows = try pairs(#"{"weeding":{"weedingMethod":"HAND"}}"#)
        #expect(rows == ["진행 방식=손으로 뽑기"])
    }

    @Test("수확")
    func harvest() throws {
        let rows = try pairs(
            #"{"harvest":{"growthPeriod":24,"growthPeriodUnit":"MONTH","harvestAmount":500,"medicinalPart":"WHOLE_HERB"}}"#
        )
        #expect(rows == ["재배 기간=24개월", "수확량=500kg", "수확 부위=전초"])
    }

    @Test("수확 - 수확량 모름")
    func harvestAmountUnknown() throws {
        let rows = try pairs(
            #"{"harvest":{"amountUnknown":true,"growthPeriod":24,"growthPeriodUnit":"YEAR","medicinalPart":"LEAF"}}"#
        )
        #expect(rows == ["재배 기간=24년", "수확량=잘 모르겠음", "수확 부위=잎"])
    }

    @Test("가지·순 정리/기타 — 상세 객체 없음 → 항목 0개")
    func noDetailObject() throws {
        let rows = try pairs(#"{"memo":"가지 정리함"}"#)
        #expect(rows.isEmpty)
    }

    @Test("미입력 항목은 행에서 제외")
    func hidesMissingFields() throws {
        let rows = try pairs(#"{"watering":{"irrigationAmount":"LOW"}}"#)
        #expect(rows == ["물의 양=조금"]) // 진행 방식 미입력 → 숨김
    }

    @Test("미상 enum은 원문으로 폴백")
    func unknownEnumFallsBackToRaw() throws {
        let rows = try pairs(#"{"weeding":{"weedingMethod":"FUTURE_X"}}"#)
        #expect(rows == ["진행 방식=FUTURE_X"])
    }
}
