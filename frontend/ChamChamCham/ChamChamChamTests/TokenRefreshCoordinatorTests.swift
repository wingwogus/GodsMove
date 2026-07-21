//
//  TokenRefreshCoordinatorTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/20/26.
//

import Foundation
import Testing
@testable import ChamChamCham

/// 세션 유지 회귀 테스트: 갱신 요청이 네트워크/서버 오류로 실패해도 토큰을 지우면 안 되고,
/// 서버가 명시적으로 401/403을 응답할 때만 세션을 종료해야 한다.
@Suite("TokenRefreshCoordinator 갱신 실패 처리")
struct TokenRefreshCoordinatorTests {
    private func makeStoreWithToken() async -> AuthTokenStore {
        let store = AuthTokenStore()
        await store.save(accessToken: "stub-access", refreshToken: "stub-refresh")
        return store
    }

    private func makeSession(protocolClass: URLProtocol.Type) -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [protocolClass]
        return URLSession(configuration: configuration)
    }

    @Test("전송 에러(연결 끊김 등)는 토큰을 지우지 않고 network 에러를 던진다")
    func transportErrorDoesNotClearToken() async {
        let tokenStore = await makeStoreWithToken()
        let session = makeSession(protocolClass: TransportErrorURLProtocol.self)
        let coordinator = TokenRefreshCoordinator(authTokenStore: tokenStore, session: session)

        do {
            try await coordinator.refreshIfNeeded()
            Issue.record("Expected refreshIfNeeded to throw")
        } catch APIError.network {
            // expected
        } catch {
            Issue.record("Expected APIError.network, got \(error)")
        }

        #expect(await tokenStore.refreshToken() == "stub-refresh")
    }

    @Test("401 응답은 토큰을 지우고 unauthorized를 던진다")
    func unauthorizedResponseClearsToken() async {
        let tokenStore = await makeStoreWithToken()
        let session = makeSession(protocolClass: UnauthorizedURLProtocol.self)
        let coordinator = TokenRefreshCoordinator(authTokenStore: tokenStore, session: session)

        do {
            try await coordinator.refreshIfNeeded()
            Issue.record("Expected refreshIfNeeded to throw")
        } catch APIError.unauthorized {
            // expected
        } catch {
            Issue.record("Expected APIError.unauthorized, got \(error)")
        }

        #expect(await tokenStore.refreshToken() == nil)
    }

    @Test("500 응답은 토큰을 지우지 않고 server 에러를 던진다")
    func serverErrorDoesNotClearToken() async {
        let tokenStore = await makeStoreWithToken()
        let session = makeSession(protocolClass: ServerErrorURLProtocol.self)
        let coordinator = TokenRefreshCoordinator(authTokenStore: tokenStore, session: session)

        do {
            try await coordinator.refreshIfNeeded()
            Issue.record("Expected refreshIfNeeded to throw")
        } catch APIError.server(let statusCode) {
            #expect(statusCode == 500)
        } catch {
            Issue.record("Expected APIError.server, got \(error)")
        }

        #expect(await tokenStore.refreshToken() == "stub-refresh")
    }

    @Test("200 응답이지만 본문이 깨진 경우 토큰을 지우지 않고 decoding 에러를 던진다")
    func malformedBodyDoesNotClearToken() async {
        let tokenStore = await makeStoreWithToken()
        let session = makeSession(protocolClass: MalformedBodyURLProtocol.self)
        let coordinator = TokenRefreshCoordinator(authTokenStore: tokenStore, session: session)

        do {
            try await coordinator.refreshIfNeeded()
            Issue.record("Expected refreshIfNeeded to throw")
        } catch APIError.decoding {
            // expected
        } catch {
            Issue.record("Expected APIError.decoding, got \(error)")
        }

        #expect(await tokenStore.refreshToken() == "stub-refresh")
    }
}

private final class TransportErrorURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
    }

    override func stopLoading() {}
}

private final class UnauthorizedURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 401,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("{}".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private final class ServerErrorURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 500,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("{}".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private final class MalformedBodyURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 200,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("not json".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
