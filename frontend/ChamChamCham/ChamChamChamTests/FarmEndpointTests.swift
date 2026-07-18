//
//  FarmEndpointTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("FarmEndpoint")
struct FarmEndpointTests {
    @Test("list and create match deployed paths")
    func pathsAndMethods() throws {
        let request = try SaveFarmRequestDTO(farm: OnboardingTestFactory.validDraft().representativeFarm)

        #expect(FarmEndpoint.list.path == "api/v1/farms")
        #expect(FarmEndpoint.list.method == .get)
        #expect(FarmEndpoint.list.body == nil)
        #expect(FarmEndpoint.create(request).path == "api/v1/farms")
        #expect(FarmEndpoint.create(request).method == .post)
        #expect(FarmEndpoint.create(request).body != nil)
        #expect(FarmEndpoint.create(request).requiresAuth)
    }

    @Test("delete targets the farm id path with DELETE and no body")
    func deletePath() {
        let id = UUID()
        let endpoint = FarmEndpoint.delete(id)

        #expect(endpoint.path == "api/v1/farms/\(id.uuidString)")
        #expect(endpoint.method == .delete)
        #expect(endpoint.body == nil)
        #expect(endpoint.requiresAuth)
    }

    @Test("update targets the farm id path with PUT and the request body")
    func updatePath() throws {
        let id = UUID()
        let request = try SaveFarmRequestDTO(farm: OnboardingTestFactory.validDraft().representativeFarm)
        let endpoint = FarmEndpoint.update(id, request)

        #expect(endpoint.path == "api/v1/farms/\(id.uuidString)")
        #expect(endpoint.method == .put)
        #expect(endpoint.body != nil)
        #expect(endpoint.requiresAuth)
    }
}
