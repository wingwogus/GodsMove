//
//  DIContainer.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftData

@MainActor
final class DIContainer {
    let modelContainer: ModelContainer
    let authTokenStore: AuthTokenStore
    let tokenRefreshCoordinator: TokenRefreshCoordinator
    let apiClient: APIClient
    let memberProfileCache: MemberProfileCache
    let pendingFarmStore: PendingFarmStore
    let pendingFarmSyncService: PendingFarmSyncService

    init(modelContainer: ModelContainer) {
        self.modelContainer = modelContainer
        let authTokenStore = AuthTokenStore()
        self.authTokenStore = authTokenStore
        let tokenRefreshCoordinator = TokenRefreshCoordinator(authTokenStore: authTokenStore)
        self.tokenRefreshCoordinator = tokenRefreshCoordinator
        let apiClient = APIClient(
            authTokenStore: authTokenStore,
            tokenRefreshCoordinator: tokenRefreshCoordinator
        )
        self.apiClient = apiClient
        self.memberProfileCache = SwiftDataMemberProfileCache(modelContext: modelContainer.mainContext)
        let pendingFarmStore = PendingFarmStore()
        self.pendingFarmStore = pendingFarmStore
        self.pendingFarmSyncService = PendingFarmSyncService(
            store: pendingFarmStore,
            repository: RemoteFarmRepository(apiClient: apiClient)
        )
    }
}

extension DIContainer {
    func makeAuthRepository() -> some AuthRepository {
        RemoteAuthRepository(apiClient: apiClient, authTokenStore: authTokenStore)
    }

    func makeOnboardingRepository() -> some OnboardingRepository {
        RemoteOnboardingRepository(apiClient: apiClient)
    }

    func makeMediaUploadRepository() -> some MediaUploadRepository {
        RemoteMediaUploadRepository(apiClient: apiClient)
    }

    func makeFarmRepository() -> some FarmRepository {
        RemoteFarmRepository(apiClient: apiClient)
    }

    func makeCropCatalogService() -> some CropCatalogService {
        RemoteCropCatalogService(apiClient: apiClient)
    }

    func makeCommunityRepository() -> some CommunityRepository {
        RemoteCommunityRepository(apiClient: apiClient)
    }

    func makeMemberProfileRepository() -> some MemberProfileRepository {
        RemoteMemberProfileRepository(apiClient: apiClient)
    }

    func makeRecordRepository() -> some RecordRepository {
        RemoteRecordRepository(apiClient: apiClient)
    }

    func makePolicyRepository() -> some PolicyRepository {
        RemotePolicyRepository(apiClient: apiClient)
    }
}
