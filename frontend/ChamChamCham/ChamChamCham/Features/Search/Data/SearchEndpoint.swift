//
//  SearchEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// `Search` tag endpoints. `all` has no cursor/size — the backend caps each section at 3 items
/// server-side. The 3 category endpoints share the same `keyword?/cursor?/size` shape as the
/// existing `RecordEndpoint.listRecords`/`CommunityEndpoint.listPosts` query-building pattern.
enum SearchEndpoint: Endpoint {
    case all(keyword: String?)
    case records(keyword: String?, cursor: String?, size: Int)
    case posts(keyword: String?, cursor: String?, size: Int)
    case policies(keyword: String?, cursor: String?, size: Int)
    case suggestions(keyword: String?)

    private static let base = "api/v1/search"

    var path: String {
        switch self {
        case .all:
            Self.base
        case .records:
            "\(Self.base)/records"
        case .posts:
            "\(Self.base)/posts"
        case .policies:
            "\(Self.base)/policies"
        case .suggestions:
            "\(Self.base)/suggestions"
        }
    }

    var method: HTTPMethod { .get }

    var body: (any Encodable & Sendable)? { nil }

    var requiresAuth: Bool { true }

    var queryItems: [URLQueryItem] {
        switch self {
        case let .all(keyword):
            keywordItem(keyword)
        case let .records(keyword, cursor, size), let .posts(keyword, cursor, size), let .policies(keyword, cursor, size):
            pageItems(keyword: keyword, cursor: cursor, size: size)
        case let .suggestions(keyword):
            keywordItem(keyword)
        }
    }

    private func keywordItem(_ keyword: String?) -> [URLQueryItem] {
        guard let keyword, !keyword.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return [URLQueryItem(name: "keyword", value: keyword)]
    }

    private func pageItems(keyword: String?, cursor: String?, size: Int) -> [URLQueryItem] {
        var items = [URLQueryItem(name: "size", value: String(size))]
        items.append(contentsOf: keywordItem(keyword))
        if let cursor {
            items.append(URLQueryItem(name: "cursor", value: cursor))
        }
        return items
    }
}
