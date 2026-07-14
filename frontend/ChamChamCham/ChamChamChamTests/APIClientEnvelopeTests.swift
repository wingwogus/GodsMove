//
//  APIClientEnvelopeTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/8/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("APIClient envelope decoding")
struct APIClientEnvelopeTests {
    @Test("EmptyDTO decodes the ApiResponse envelope before returning success")
    func emptyDTOHonorsEnvelopeError() async throws {
        let session = URLSession.envelopeErrorStub
        let tokenStore = AuthTokenStore()
        let client = APIClient(
            authTokenStore: tokenStore,
            tokenRefreshCoordinator: TokenRefreshCoordinator(authTokenStore: tokenStore, session: session),
            session: session
        )

        do {
            let _: EmptyDTO = try await client.send(StubEndpoint(path: "api/v1/auth/logout", method: .post))
            Issue.record("Expected APIError.apiError, but the request returned EmptyDTO.")
        } catch let APIError.apiError(code, message) {
            #expect(code == "AUTH_REQUIRED")
            #expect(message == "로그인이 필요합니다")
        } catch {
            Issue.record("Expected APIError.apiError, but received \(error).")
        }
    }
}

private struct StubEndpoint: Endpoint {
    let path: String
    let method: HTTPMethod
    var body: (any Encodable & Sendable)? { nil }
    var requiresAuth: Bool { false }
}

private extension URLSession {
    static var envelopeErrorStub: URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [EnvelopeErrorURLProtocol.self]
        return URLSession(configuration: configuration)
    }
}

private final class EnvelopeErrorURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        let data = Data("""
        {
          "success": false,
          "data": null,
          "error": {
            "code": "AUTH_REQUIRED",
            "message": "로그인이 필요합니다"
          }
        }
        """.utf8)

        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 200,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!

        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
