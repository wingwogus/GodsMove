//
//  ReportEndpointTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("ReportEndpoint")
struct ReportEndpointTests {
    @Test("work items use the authenticated GET contract")
    func workItemsContract() {
        let endpoint = ReportEndpoint.workItems(ReportQuery())

        #expect(endpoint.path == "api/v1/farming-reports/work-items")
        #expect(endpoint.method == .get)
        #expect(endpoint.body == nil)
        #expect(endpoint.requiresAuth)
    }

    @Test("work items omit absent filters and absent or blank cursors")
    func workItemsOmitAbsentValues() {
        let nilCursor = queryDictionary(.workItems(ReportQuery(cursor: nil)))
        let blankCursor = queryDictionary(.workItems(ReportQuery(cursor: " \t\n ")))

        #expect(nilCursor == ["size": "20"])
        #expect(blankCursor == ["size": "20"])
    }

    @Test("work items serialize every provided filter and preserve the opaque cursor")
    func workItemsSerializeFilters() {
        let farmId = UUID()
        let cropId = UUID()
        let cursor = "eyJpZCI6IjEifQ==&next"
        let query = queryDictionary(.workItems(ReportQuery(
            farmIds: [farmId],
            cropIds: [cropId],
            workTypes: [.pestControl],
            cursor: cursor,
            size: 37
        )))

        #expect(query == [
            "farmId": farmId.uuidString,
            "cropId": cropId.uuidString,
            "workType": "PEST_CONTROL",
            "cursor": cursor,
            "size": "37",
        ])
    }

    @Test("work items serialize multiple selected work types as repeated query items")
    func workItemsSerializeMultipleWorkTypes() {
        let values = ReportEndpoint.workItems(ReportQuery(workTypes: [.watering, .harvest])).queryItems
            .filter { $0.name == "workType" }
            .compactMap(\.value)

        #expect(Set(values) == [WorkType.watering.rawValue, WorkType.harvest.rawValue])
    }

    @Test("work items serialize multiple selected farms and crops as repeated query items")
    func workItemsSerializeMultipleFarmsAndCrops() {
        let farmA = UUID()
        let farmB = UUID()
        let cropA = UUID()
        let cropB = UUID()
        let items = ReportEndpoint.workItems(ReportQuery(farmIds: [farmA, farmB], cropIds: [cropA, cropB])).queryItems

        let farmValues = Set(items.filter { $0.name == "farmId" }.compactMap(\.value))
        let cropValues = Set(items.filter { $0.name == "cropId" }.compactMap(\.value))
        #expect(farmValues == [farmA.uuidString, farmB.uuidString])
        #expect(cropValues == [cropA.uuidString, cropB.uuidString])
    }

    @Test("detail and feedback endpoints match deployed paths")
    func readPaths() {
        let reportId = UUID()
        let detail = ReportEndpoint.workDetail(reportId: reportId, workType: .harvest)
        let feedback = ReportEndpoint.feedback(reportId: reportId)

        #expect(detail.path == "api/v1/farming-reports/\(reportId.uuidString)/work-types/HARVEST")
        #expect(detail.method == .get)
        #expect(detail.body == nil)
        #expect(detail.requiresAuth)

        #expect(feedback.path == "api/v1/farming-reports/\(reportId.uuidString)/feedback")
        #expect(feedback.method == .get)
        #expect(feedback.body == nil)
        #expect(feedback.requiresAuth)
    }

    @Test("regenerate uses authenticated POST without a body")
    func regenerateContract() {
        let reportId = UUID()
        let endpoint = ReportEndpoint.regenerate(reportId: reportId, workType: .watering)

        #expect(endpoint.path
            == "api/v1/farming-reports/\(reportId.uuidString)/feedback/WATERING/regenerate")
        #expect(endpoint.method == .post)
        #expect(endpoint.body == nil)
        #expect(endpoint.requiresAuth)
        #expect(endpoint.queryItems.isEmpty)
    }

    private func queryDictionary(_ endpoint: ReportEndpoint) -> [String: String] {
        Dictionary(uniqueKeysWithValues: endpoint.queryItems.map { ($0.name, $0.value ?? "") })
    }
}
