//
//  TokenRefreshCoordinator.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

actor TokenRefreshCoordinator {
    private let authTokenStore: AuthTokenStore
    private let session: URLSession
    private let baseURL: URL
    private var inFlightRefresh: Task<Void, Error>?

    init(
        authTokenStore: AuthTokenStore,
        session: URLSession = .shared,
        baseURL: URL = APIEnvironment.baseURL
    ) {
        self.authTokenStore = authTokenStore
        self.session = session
        self.baseURL = baseURL
    }

    /// Coalesces concurrent 401s into a single in-flight `/reissue` call.
    func refreshIfNeeded() async throws {
        if let existing = inFlightRefresh {
            return try await existing.value
        }
        let task = Task { try await performRefresh() }
        inFlightRefresh = task
        defer { inFlightRefresh = nil }
        try await task.value
    }

    /// Talks to `/api/v1/auth/reissue` directly via its own `URLSession`, independent of `APIClient`/`Endpoint` —
    /// APIClient is what calls *this*, so a back-reference would cycle, and a 401 mid-reissue must never recurse.
    private func performRefresh() async throws {
        guard let refreshToken = await authTokenStore.refreshToken() else {
            throw APIError.unauthorized
        }

        var request = URLRequest(url: baseURL.appendingPathComponent("api/v1/auth/reissue"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONEncoder().encode(ReissueRequestBody(refreshToken: refreshToken))

        guard
            let (data, response) = try? await session.data(for: request),
            let http = response as? HTTPURLResponse,
            (200...299).contains(http.statusCode),
            let envelope = try? JSONDecoder().decode(APIEnvelope<TokenResponseDTO>.self, from: data),
            envelope.success,
            let pair = envelope.data
        else {
            await authTokenStore.clear()
            throw APIError.unauthorized
        }

        await authTokenStore.save(accessToken: pair.accessToken, refreshToken: pair.refreshToken)
    }
}

private struct ReissueRequestBody: Encodable, Sendable {
    let refreshToken: String
}
