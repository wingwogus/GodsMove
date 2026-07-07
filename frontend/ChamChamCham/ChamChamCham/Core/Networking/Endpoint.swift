//
//  Endpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

protocol Endpoint: Sendable {
    var path: String { get }
    var method: HTTPMethod { get }
    var body: (any Encodable & Sendable)? { get }
    var requiresAuth: Bool { get }
    var headers: [String: String] { get }
    var queryItems: [URLQueryItem] { get }
}

extension Endpoint {
    var headers: [String: String] { [:] }
    var queryItems: [URLQueryItem] { [] }
}

enum HTTPMethod: String, Sendable {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case patch = "PATCH"
    case delete = "DELETE"
}
