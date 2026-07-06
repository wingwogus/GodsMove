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

    init(modelContainer: ModelContainer) {
        self.modelContainer = modelContainer
        let authTokenStore = AuthTokenStore()
        self.authTokenStore = authTokenStore
        let tokenRefreshCoordinator = TokenRefreshCoordinator(authTokenStore: authTokenStore)
        self.tokenRefreshCoordinator = tokenRefreshCoordinator
        self.apiClient = APIClient(
            authTokenStore: authTokenStore,
            tokenRefreshCoordinator: tokenRefreshCoordinator
        )
        self.memberProfileCache = SwiftDataMemberProfileCache(modelContext: modelContainer.mainContext)
    }
}

extension DIContainer {
    func makeAuthRepository() -> some AuthRepository {
        RemoteAuthRepository(apiClient: apiClient, authTokenStore: authTokenStore)
    }

    func makeOnboardingRepository() -> some OnboardingRepository {
        RemoteOnboardingRepository(apiClient: apiClient)
    }

    func makeCropCatalogService() -> some CropCatalogService {
        RemoteCropCatalogService(apiClient: apiClient)
    }
}
