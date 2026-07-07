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
        tokenRefreshCoordinator: TokenRefreshCoordinator,
        session: URLSession = .shared
    ) {
        self.authTokenStore = authTokenStore
        self.tokenRefreshCoordinator = tokenRefreshCoordinator
        self.session = session
    }

    func send<T: Decodable & Sendable>(_ endpoint: Endpoint) async throws -> T {
        try await send(endpoint, isRetry: false)
    }

    private func send<T: Decodable & Sendable>(_ endpoint: Endpoint, isRetry: Bool) async throws -> T {
        var request = try makeRequest(for: endpoint)
        if endpoint.requiresAuth, let token = await authTokenStore.accessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.network(error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw APIError.network(URLError(.badServerResponse))
        }

        if http.statusCode == 401, endpoint.requiresAuth, !isRetry {
            try await tokenRefreshCoordinator.refreshIfNeeded()
            return try await send(endpoint, isRetry: true)
        }

        if T.self == EmptyDTO.self, (200...299).contains(http.statusCode) {
            return EmptyDTO() as! T // swiftlint:disable:this force_cast — guarded by the metatype check above
        }

        guard let envelope = try? JSONDecoder().decode(APIEnvelope<T>.self, from: data) else {
            throw APIError.server(statusCode: http.statusCode)
        }

        guard envelope.success else {
            throw APIError.apiError(
                code: envelope.error?.code ?? "UNKNOWN",
                message: envelope.error?.message ?? ""
            )
        }

        guard let value = envelope.data else {
            throw APIError.decoding(DecodingError.valueNotFound(
                T.self,
                .init(codingPath: [], debugDescription: "success=true but data was null")
            ))
        }
        return value
    }

    private func makeRequest(for endpoint: Endpoint) throws -> URLRequest {
        let base = APIEnvironment.baseURL.appendingPathComponent(endpoint.path)
        var components = URLComponents(url: base, resolvingAgainstBaseURL: false)
        if !endpoint.queryItems.isEmpty {
            components?.queryItems = endpoint.queryItems
        }
        guard let url = components?.url else {
            throw APIError.network(URLError(.badURL))
        }
        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        for (field, value) in endpoint.headers {
            request.setValue(value, forHTTPHeaderField: field)
        }
        if let body = endpoint.body {
            request.httpBody = try JSONEncoder().encode(body)
        }
        return request
    }
}
