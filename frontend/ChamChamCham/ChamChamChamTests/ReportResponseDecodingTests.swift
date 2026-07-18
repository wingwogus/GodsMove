//
//  ReportResponseDecodingTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Report response decoding + domain mapping")
struct ReportResponseDecodingTests {
    private let reportId = "11111111-1111-1111-1111-111111111111"
    private let farmId = "22222222-2222-2222-2222-222222222222"
    private let cropId = "33333333-3333-3333-3333-333333333333"

    private func decode<T: Decodable & Sendable>(_ type: T.Type, json: String) throws -> T {
        try JSONDecoder().decode(T.self, from: Data(json.utf8))
    }

    @Test("work item page maps composite keys, nullable fields, cursor, and future values safely")
    func workItemPage() throws {
        let json = """
        {
          "items": [
            {
              "reportId": "\(reportId)", "status": "ACTIVE",
              "farmId": "\(farmId)", "farmName": "북쪽 밭",
              "cropId": "\(cropId)", "cropName": "황기",
              "startsAt": "2026-04-01T09:30:00", "endsAt": null,
              "workType": "PLANTING", "workTypeLabel": "심기",
              "recordCount": 3, "lastWorkedOn": null, "thumbnailUrl": null
            },
            {
              "reportId": "\(reportId)", "status": "ARCHIVED",
              "farmId": "\(farmId)", "farmName": "북쪽 밭",
              "cropId": "\(cropId)", "cropName": "황기",
              "startsAt": "2026-04-01T09:30:00.125", "endsAt": "2026-10-31T18:00:00",
              "workType": "HARVEST", "workTypeLabel": "수확",
              "recordCount": 2, "lastWorkedOn": "2026-10-30", "thumbnailUrl": "https://img/report.jpg"
            },
            {
              "reportId": "\(reportId)", "status": "COMPLETED",
              "farmId": "\(farmId)", "farmName": "북쪽 밭",
              "cropId": "\(cropId)", "cropName": "황기",
              "startsAt": "2026-04-01T09:30:00", "endsAt": null,
              "workType": "PROCESSING", "workTypeLabel": "가공",
              "recordCount": 1, "lastWorkedOn": null, "thumbnailUrl": null
            }
          ],
          "nextCursor": "opaque:cursor/2+next=="
        }
        """

        let page = try decode(FarmingWorkReportPageResponseDTO.self, json: json).toDomain()

        #expect(page.items.count == 2)
        #expect(page.nextCursor == "opaque:cursor/2+next==")
        #expect(page.items[0].id == WorkReportKey(
            reportId: UUID(uuidString: reportId)!,
            workType: .planting
        ))
        #expect(page.items[0].status == .active)
        #expect(page.items[0].endsAt == nil)
        #expect(page.items[0].lastWorkedOn == nil)
        #expect(page.items[0].thumbnailUrl == nil)
        #expect(page.items[1].status == .unsupported("ARCHIVED"))
        #expect(page.items[1].endsAt != nil)
        #expect(page.items[1].lastWorkedOn != nil)
    }

    @Test("each supported typed statistics branch decodes when sibling keys are omitted")
    func typedStatisticsBranches() throws {
        let fixtures: [(WorkType, String, String)] = [
            (.planting, "planting", "\"propagationMethods\": [{\"code\":\"SEED\",\"label\":\"종자\",\"recordCount\":2,\"recordRatePct\":66.7,\"totalQuantity\":120.5,\"quantityUnit\":\"립\",\"quantityCoverage\":{\"recordedCount\":2,\"targetCount\":3}}]"),
            (.watering, "watering", "\"amountDistribution\": [{\"code\":\"LARGE\",\"label\":\"많이\",\"count\":2,\"ratePct\":100}], \"methodDistribution\": []"),
            (.fertilizing, "fertilizing", "\"totalAmountKg\":12.5,\"averageAmountKg\":6.25,\"amountCoverage\":{\"recordedCount\":2,\"targetCount\":2},\"materialCategories\":[{\"code\":\"COMPOST\",\"label\":\"퇴비\",\"recordCount\":2,\"recordRatePct\":100,\"amountKg\":12.5,\"amountRatePct\":100}],\"methodDistribution\":[],\"categoryMethods\":[]"),
            (.pestControl, "pestControl", "\"categoryDistribution\":[],\"pesticideAmounts\":[{\"unit\":\"ml\",\"amount\":250.5,\"coverage\":{\"recordedCount\":1,\"targetCount\":1}}],\"categoryAmounts\":[],\"totalSprayAmountMl\":20,\"sprayAmountCoverage\":{\"recordedCount\":1,\"targetCount\":1},\"targets\":[{\"target\":\"진딧물\",\"count\":1}]"),
            (.weeding, "weeding", "\"methodDistribution\": [{\"code\":\"HAND\",\"label\":\"손제초\",\"count\":2,\"ratePct\":100}]"),
            (.harvest, "harvest", "\"totalAmountKg\":32.5,\"averageAmountKg\":16.25,\"amountCoverage\":{\"recordedCount\":2,\"targetCount\":2},\"firstHarvestedOn\":\"2026-09-01\",\"lastHarvestedOn\":\"2026-09-10\",\"medicinalParts\":[{\"code\":\"ROOT\",\"label\":\"뿌리\",\"recordCount\":2,\"recordRatePct\":100,\"knownAmountKg\":32.5,\"amountRatePct\":100,\"amountCoverage\":{\"recordedCount\":2,\"targetCount\":2}}],\"finalGrowthPeriodMonths\":5,\"growthPeriodRangeMonths\":{\"minMonths\":4,\"maxMonths\":6}")
        ]

        for (workType, key, extra) in fixtures {
            let detail = try decode(
                FarmingWorkReportDetailResponseDTO.self,
                json: detailJSON(workType: workType.rawValue, branch: "\"\(key)\": {\(commonFields),\(extra)}")
            ).toDomain()

            #expect(detail.key.workType == workType)
            #expect(detail.statistics.common.recordCount == 2)
            switch workType {
            case .planting: #expect(detail.statistics.planting?.propagationMethods.first?.totalQuantity == Decimal(string: "120.5"))
            case .watering: #expect(detail.statistics.watering?.amountDistribution.count == 1)
            case .fertilizing: #expect(detail.statistics.fertilizing?.totalAmountKg == Decimal(string: "12.5"))
            case .pestControl: #expect(detail.statistics.pestControl?.pesticideAmounts.first?.amount == Decimal(string: "250.5"))
            case .weeding: #expect(detail.statistics.weeding?.methodDistribution.count == 1)
            case .harvest: #expect(detail.statistics.harvest?.growthPeriodRangeMonths?.maxMonths == 6)
            case .pruning, .etc: Issue.record("common-only types are covered separately")
            }
        }
    }

    @Test("pruning and etc details map common statistics without a typed branch")
    func commonOnlyDetails() throws {
        for workType in [WorkType.pruning, .etc] {
            let detail = try decode(
                FarmingWorkReportDetailResponseDTO.self,
                json: detailJSON(workType: workType.rawValue, branch: "")
            ).toDomain()

            #expect(detail.key.workType == workType)
            #expect(detail.statistics.common.workedDayCount == 2)
            #expect(detail.statistics.hasTypedStatistics == false)
        }
    }

    @Test("detail embedded feedback uses content and preserves pending without content")
    func embeddedFeedback() throws {
        let ready = try decode(
            FarmingWorkReportDetailResponseDTO.self,
            json: detailJSON(
                workType: WorkType.pruning.rawValue,
                branch: "",
                feedback: """
                {"status":"READY","content":{"summary":"좋은 흐름이에요","comparisons":[{"text":"지난달보다 꾸준해요"}],"strengths":[{"text":"간격이 안정적이에요"}],"improvements":[],"nextActions":[{"text":"다음 주에도 이어가세요"}]}}
                """
            )
        ).toDomain()
        #expect(ready.feedback?.state == .ready)
        #expect(ready.feedback?.content?.summary == "좋은 흐름이에요")
        #expect(ready.feedback?.content?.comparisons == ["지난달보다 꾸준해요"])

        let pending = try decode(
            FarmingWorkReportDetailResponseDTO.self,
            json: detailJSON(workType: WorkType.pruning.rawValue, branch: "", feedback: "{\"status\":\"PENDING\",\"content\":null}")
        ).toDomain()
        #expect(pending.feedback?.state == .pending)
        #expect(pending.feedback?.content == nil)
    }

    @Test("bulk feedback uses feedback key and supports every state plus future values")
    func bulkFeedback() throws {
        let json = """
        {
          "reportId": "\(reportId)",
          "feedbacks": [
            \(bulkItem(id: "00000000-0000-0000-0000-000000000001", workType: "PLANTING", status: "PENDING", feedback: "null")),
            \(bulkItem(id: "00000000-0000-0000-0000-000000000002", workType: "WATERING", status: "FAILED", failureCode: "AI_TIMEOUT", feedback: "null")),
            \(bulkItem(id: "00000000-0000-0000-0000-000000000003", workType: "FERTILIZING", status: "STALE", feedback: "null")),
            \(bulkItem(id: "00000000-0000-0000-0000-000000000004", workType: "HARVEST", status: "READY", feedback: "{\"summary\":\"수확 코칭\",\"comparisons\":[],\"strengths\":[],\"improvements\":[],\"nextActions\":[]}")),
            \(bulkItem(id: "00000000-0000-0000-0000-000000000005", workType: "ETC", status: "QUEUED", feedback: "null"))
          ]
        }
        """

        let feedback = try decode(ReportFeedbackListResponseDTO.self, json: json).toDomain()

        #expect(feedback.reportId == UUID(uuidString: reportId))
        #expect(feedback.feedbacks.map(\.state) == [.pending, .failed, .stale, .ready, .unsupported("QUEUED")])
        #expect(feedback.feedbacks[1].failureCode == "AI_TIMEOUT")
        #expect(feedback.feedbacks[3].content?.summary == "수확 코칭")
        #expect(feedback.feedbacks[3].createdAt != .distantPast)
    }

    @Test("completed detail can remain pending while bulk feedback is empty")
    func syntheticPendingAndEmptyBulk() throws {
        let detail = try decode(
            FarmingWorkReportDetailResponseDTO.self,
            json: detailJSON(workType: WorkType.etc.rawValue, branch: "", feedback: "{\"status\":\"PENDING\",\"content\":null}")
        ).toDomain()
        let bulk = try decode(
            ReportFeedbackListResponseDTO.self,
            json: "{\"reportId\":\"\(reportId)\",\"feedbacks\":[]}"
        ).toDomain()

        #expect(detail.status == .completed)
        #expect(detail.feedback?.state == .pending)
        #expect(bulk.feedbacks.isEmpty)
    }

    @Test("active detail decodes without feedback")
    func activeDetailWithoutFeedback() throws {
        let detail = try decode(
            FarmingWorkReportDetailResponseDTO.self,
            json: detailJSON(status: "ACTIVE", workType: WorkType.pruning.rawValue, branch: "")
        ).toDomain()

        #expect(detail.status == .active)
        #expect(detail.feedback == nil)
    }

    @Test("detail DTO round trips through Codable for offline cache storage")
    func detailCodableRoundTrip() throws {
        let original = try decode(
            FarmingWorkReportDetailResponseDTO.self,
            json: detailJSON(
                workType: WorkType.planting.rawValue,
                branch: "\"planting\": {\(commonFields),\"propagationMethods\":[]}"
            )
        )

        let encoded = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(FarmingWorkReportDetailResponseDTO.self, from: encoded)
        let detail = try decoded.toDomain()

        #expect(detail.key.workType == .planting)
        #expect(detail.statistics.planting?.propagationMethods.isEmpty == true)
        #expect(detail.statistics.common.recordCount == 2)
    }

    @Test("unknown work type is an explicit error for detail")
    func unknownDetailWorkType() throws {
        let dto = try decode(
            FarmingWorkReportDetailResponseDTO.self,
            json: detailJSON(workType: "PROCESSING", branch: "")
        )

        #expect(throws: ReportMappingError.self) {
            try dto.toDomain()
        }
    }

    @Test("report date parser accepts local date and fractional local date-time with stable display")
    func reportDates() throws {
        let day = try #require(ReportDateParser.localDate(from: "2026-07-16"))
        let timestamp = try #require(ReportDateParser.localDateTime(from: "2026-07-16T09:30:15.250"))

        #expect(ReportDateParser.displayDate(day) == "2026.07.16")
        #expect(ReportDateParser.displayDate(timestamp) == "2026.07.16")
        #expect(ReportDateParser.localDate(from: "invalid") == nil)
    }

    private var commonFields: String {
        """
        "recordCount":2,"firstWorkedOn":"2026-04-01","lastWorkedOn":"2026-04-10","workedDayCount":2,"averageIntervalDays":9.0,"photoAttachedRecordCount":1,"photoAttachmentRatePct":50.0,"weatherDistribution":[{"code":"SUNNY","label":"맑음","count":2,"ratePct":100}],"averageTemperatureC":18.5
        """
    }

    private func detailJSON(
        status: String = "COMPLETED",
        workType: String,
        branch: String,
        feedback: String = "null"
    ) -> String {
        let branchField = branch.isEmpty ? "" : ",\(branch)"
        return """
        {
          "reportId":"\(reportId)","status":"\(status)","workType":"\(workType)","workTypeLabel":"작업",
          "farmId":"\(farmId)","farmName":"북쪽 밭","cropId":"\(cropId)","cropName":"황기",
          "startsAt":"2026-04-01T09:30:00","endsAt":"2026-10-31T18:00:00",
          "statistics":{"common":{\(commonFields)}\(branchField)},
          "feedback":\(feedback)
        }
        """
    }

    private func bulkItem(
        id: String,
        workType: String,
        status: String,
        failureCode: String? = nil,
        feedback: String
    ) -> String {
        let failure = failureCode.map { "\"\($0)\"" } ?? "null"
        return """
        {"feedbackId":"\(id)","workType":"\(workType)","status":"\(status)","inputPrepared":true,"failureCode":\(failure),"feedback":\(feedback),"createdAt":"2026-07-16T09:00:00","updatedAt":"2026-07-16T09:05:00"}
        """
    }
}
