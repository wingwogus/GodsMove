//
//  CropCategoryEndpointTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/8/26.
//

import Testing
@testable import ChamChamCham

@Suite("CropEndpoint category paths")
struct CropCategoryEndpointTests {
    @Test("category crop endpoint matches Swagger")
    func categoryPath() {
        let endpoint = CropEndpoint.categoryCrops("ROOT_BARK")

        #expect(endpoint.path == "api/v1/crops/categories/ROOT_BARK/crops")
        #expect(endpoint.method == .get)
        #expect(endpoint.requiresAuth)
        #expect(endpoint.body == nil)
    }
}
