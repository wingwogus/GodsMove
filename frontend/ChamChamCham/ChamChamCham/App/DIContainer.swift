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
    let apiClient: APIClient
    let syncEngine: SyncEngine

    init(modelContainer: ModelContainer) {
        self.modelContainer = modelContainer
        self.authTokenStore = AuthTokenStore()
        self.apiClient = APIClient(authTokenStore: authTokenStore)
        self.syncEngine = SyncEngine()
    }
}
