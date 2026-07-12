//
//  FarmDTOTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Farm DTO contract")
struct FarmDTOTests {
    @Test("standalone save includes cropIds and omits farmId")
    func saveRequestShape() throws {
        let cropId = UUID()
        let farm = OnboardingFarmDraft(
            cropIDs: [cropId],
            farmName: "두번째농장",
            farmRoadAddress: "전북 전주시 둘길 2",
            farmJibunAddress: "전북 전주시 둘동 2",
            farmLatitude: 35.2,
            farmLongitude: 127.2
        )

        let data = try JSONEncoder().encode(SaveFarmRequestDTO(farm: farm))
        let json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(json["farmId"] == nil)
        #expect(json["name"] as? String == "두번째농장")
        #expect((json["cropIds"] as? [String]) == [cropId.uuidString])
    }

    @Test("response decodes standalone farmId and crops")
    func responseShape() throws {
        let farmId = UUID()
        let cropId = UUID()
        let json = """
        {
          "farmId":"\(farmId)",
          "name":"두번째농장",
          "roadAddress":"전북 전주시 둘길 2",
          "jibunAddress":null,
          "latitude":35.2,
          "longitude":127.2,
          "pnu":null,
          "landCategory":null,
          "areaSqm":null,
          "areaIsManualEntry":false,
          "boundaryCoordinates":[],
          "dataSource":{},
          "crops":[{
            "id":"\(cropId)",
            "externalNo":422,
            "name":"황기",
            "usePartCategory":"ROOT_BARK",
            "usePartCategoryLabel":"뿌리·껍질"
          }]
        }
        """

        let response = try JSONDecoder().decode(StandaloneFarmResponseDTO.self, from: Data(json.utf8))

        #expect(response.farmId == farmId)
        #expect(response.crops.map(\.id) == [cropId])
    }
}
