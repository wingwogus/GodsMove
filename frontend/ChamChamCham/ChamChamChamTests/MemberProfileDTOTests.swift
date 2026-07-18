//
//  MemberProfileDTOTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/8/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Member profile API contracts")
struct MemberProfileDTOTests {
    @Test("decodes my profile response from Swagger shape")
    func myProfile() throws {
        let json = """
        {
          "memberId": "11111111-1111-1111-1111-111111111111",
          "email": "member@example.com",
          "name": "홍길동",
          "phone": "010-1234-5678",
          "birthDate": "1990-01-01",
          "nickname": "길동",
          "experienceLevel": 7,
          "managementType": "AGRICULTURAL_INDIVIDUAL",
          "profileImageUrl": null,
          "farms": [
            {
              "farmId": "22222222-2222-2222-2222-222222222222",
              "name": "참참농장",
              "roadAddress": "전북특별자치도 전주시 덕진구 예시로 12",
              "jibunAddress": null,
              "displayRegion": "전주시 덕진구"
            }
          ],
          "crops": [
            {
              "cropId": "33333333-3333-3333-3333-333333333333",
              "cropName": "황기"
            }
          ]
        }
        """

        let dto = try JSONDecoder().decode(MyProfileResponseDTO.self, from: Data(json.utf8))
        let profile = dto.toDomain()

        #expect(profile.memberId == UUID(uuidString: "11111111-1111-1111-1111-111111111111"))
        #expect(profile.email == "member@example.com")
        #expect(profile.farms.first?.displayRegion == "전주시 덕진구")
        #expect(profile.crops.first?.cropName == "황기")
    }

    @Test("decodes public profile response from Swagger shape")
    func publicProfile() throws {
        let json = """
        {
          "memberId": "11111111-1111-1111-1111-111111111111",
          "nickname": "길동",
          "experienceLevel": 7,
          "managementType": "AGRICULTURAL_INDIVIDUAL",
          "profileImageUrl": "https://img/profile.jpg",
          "farms": [
            {
              "farmId": "22222222-2222-2222-2222-222222222222",
              "displayRegion": "전주시 덕진구"
            }
          ],
          "crops": [
            {
              "cropId": "33333333-3333-3333-3333-333333333333",
              "cropName": "황기"
            }
          ]
        }
        """

        let dto = try JSONDecoder().decode(PublicProfileResponseDTO.self, from: Data(json.utf8))
        let profile = dto.toDomain()

        #expect(profile.memberId == UUID(uuidString: "11111111-1111-1111-1111-111111111111"))
        #expect(profile.nickname == "길동")
        #expect(profile.farms.first?.displayRegion == "전주시 덕진구")
        #expect(profile.crops.first?.cropName == "황기")
    }

    @Test("encodes profile update request with its required farms array")
    func updateRequest() throws {
        let farmId = try #require(UUID(uuidString: "22222222-2222-2222-2222-222222222222"))
        let cropId = try #require(UUID(uuidString: "33333333-3333-3333-3333-333333333333"))
        let request = UpdateMyProfileRequestDTO(
            name: "홍길동",
            phone: "010-1234-5678",
            birthDate: "1990-01-01",
            nickname: "길동",
            experienceLevel: 7,
            managementType: "AGRICULTURAL_INDIVIDUAL",
            profileMediaId: nil,
            farms: [
                UpdateMyProfileFarmRequestDTO(
                    farmId: farmId,
                    name: "참참농장",
                    roadAddress: "전북특별자치도 전주시 덕진구 예시로 12",
                    jibunAddress: nil,
                    latitude: 35.8,
                    longitude: 127.1,
                    pnu: nil,
                    landCategory: nil,
                    areaSqm: nil,
                    areaIsManualEntry: true,
                    boundaryCoordinates: [],
                    dataSource: .onboardingJusoVWorld,
                    cropIds: [cropId]
                )
            ]
        )

        let data = try JSONEncoder().encode(request)
        let json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let farms = try #require(json["farms"] as? [[String: Any]])

        #expect(json["name"] as? String == "홍길동")
        #expect(farms.first?["farmId"] as? String == farmId.uuidString)
        #expect(farms.first?["cropIds"] as? [String] == [cropId.uuidString])
    }

    @Test("member endpoints match Swagger paths")
    func endpointShapes() throws {
        let memberId = try #require(UUID(uuidString: "11111111-1111-1111-1111-111111111111"))
        let update = UpdateMyProfileRequestDTO(
            name: "홍길동",
            phone: "010-1234-5678",
            birthDate: "1990-01-01",
            nickname: "길동",
            experienceLevel: 7,
            managementType: "AGRICULTURAL_INDIVIDUAL",
            profileMediaId: nil,
            farms: []
        )

        #expect(MemberEndpoint.myProfile.path == "api/v1/members/me")
        #expect(MemberEndpoint.myProfile.method == .get)
        #expect(MemberEndpoint.publicProfile(memberId).path == "api/v1/members/\(memberId.uuidString)/profile")
        #expect(MemberEndpoint.publicProfile(memberId).method == .get)
        #expect(MemberEndpoint.updateMyProfile(update).path == "api/v1/members/me/profile")
        #expect(MemberEndpoint.updateMyProfile(update).method == .put)
        #expect(MemberEndpoint.updateMyProfile(update).body != nil)
        #expect(MemberEndpoint.updateMyProfile(update).requiresAuth)
    }
}
