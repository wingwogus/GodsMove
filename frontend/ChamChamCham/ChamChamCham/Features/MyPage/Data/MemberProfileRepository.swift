//
//  MemberProfileRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

protocol MemberProfileRepository: Sendable {
    func fetchMyProfile() async throws -> MyMemberProfile
    func fetchPublicProfile(memberId: UUID) async throws -> PublicMemberProfile
    func updateMyProfile(_ request: UpdateMyProfileRequestDTO) async throws -> MyMemberProfile
}

struct RemoteMemberProfileRepository: MemberProfileRepository {
    let apiClient: APIClient

    func fetchMyProfile() async throws -> MyMemberProfile {
        let dto: MyProfileResponseDTO = try await apiClient.send(MemberEndpoint.myProfile)
        return dto.toDomain()
    }

    func fetchPublicProfile(memberId: UUID) async throws -> PublicMemberProfile {
        let dto: PublicProfileResponseDTO = try await apiClient.send(MemberEndpoint.publicProfile(memberId))
        return dto.toDomain()
    }

    func updateMyProfile(_ request: UpdateMyProfileRequestDTO) async throws -> MyMemberProfile {
        let dto: MyProfileResponseDTO = try await apiClient.send(MemberEndpoint.updateMyProfile(request))
        return dto.toDomain()
    }
}
