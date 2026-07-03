//
//  APIClient.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

actor APIClient {
    private let authTokenStore: AuthTokenStore
    private let tokenRefreshCoordinator: TokenRefreshCoordinator
    private let session: URLSession

    init(
        authTokenStore: AuthTokenStore,
        tokenRefreshCoordinator: TokenRefreshCoordinator = TokenRefreshCoordinator(),
        session: URLSession = .shared
    ) {
        self.authTokenStore = authTokenStore
        self.tokenRefreshCoordinator = tokenRefreshCoordinator
        self.session = session
    }

    func send<T: Decodable>(_ endpoint: Endpoint) async throws -> T {
        fatalError("not implemented")
    }
}
